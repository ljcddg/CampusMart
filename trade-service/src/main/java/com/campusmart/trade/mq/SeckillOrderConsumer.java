package com.campusmart.trade.mq;

import com.campusmart.common.constant.MqConstants;
import com.campusmart.trade.dto.SeckillOrderMessage;
import com.campusmart.trade.entity.Order;
import com.campusmart.trade.entity.SeckillActivity;
import com.campusmart.trade.mapper.OrderMapper;
import com.campusmart.trade.mapper.SeckillActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 秒杀订单消费者。
 *
 * <p>链路：幂等校验 → MySQL 乐观锁扣减库存 → 写入订单 → ACK。
 *
 * <p>失败处理策略：
 * <ul>
 *   <li>业务失败（库存不足 / 版本冲突 / 活动不存在）：回补 Redis 预扣、落失败流水，正常返回不触发重试；</li>
 *   <li>系统异常（数据库连接抖动等）：抛异常交由 RocketMQ 重试；</li>
 *   <li>幂等：order_no 唯一索引 + 消费前查重，重复消息直接忽略。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_SECKILL_ORDER,
        consumerGroup = MqConstants.CONSUMER_GROUP_SECKILL_ORDER,
        selectorExpression = MqConstants.TAG_SECKILL_ORDER)
public class SeckillOrderConsumer implements RocketMQListener<SeckillOrderMessage> {

    private final OrderMapper orderMapper;
    private final SeckillActivityMapper activityMapper;
    private final com.campusmart.trade.component.SeckillStockRedisService stockRedisService;

    @Override
    public void onMessage(SeckillOrderMessage message) {
        String orderNo = message.getOrderNo();
        log.info("开始消费秒杀订单消息: orderNo={}, activityId={}, userId={}",
                orderNo, message.getActivityId(), message.getUserId());

        // 1. 幂等校验：订单号已存在说明已处理过
        if (orderMapper.selectByOrderNo(orderNo) != null) {
            log.info("订单已存在，跳过重复消息: orderNo={}", orderNo);
            return;
        }

        try {
            // 2. 第二道防线：MySQL 乐观锁扣减库存
            SeckillActivity activity = activityMapper.selectById(message.getActivityId());
            if (activity == null) {
                handleBusinessFailure(message, "秒杀活动不存在");
                return;
            }
            int rows = activityMapper.deductStock(activity.getId(), activity.getVersion());
            if (rows == 0) {
                // Redis 与 MySQL 库存不一致（或并发版本冲突），回补 Redis 并落失败流水
                handleBusinessFailure(message, "MySQL 乐观锁扣减失败：库存不足或版本冲突");
                return;
            }

            // 3. 创建订单
            Order order = buildOrder(message, 1, null);
            try {
                orderMapper.insert(order);
            } catch (DuplicateKeyException e) {
                // 并发重复消息：唯一索引拦截，回补本次多扣的库存
                activityMapper.restoreStock(activity.getId());
                stockRedisService.rollback(message.getActivityId(), message.getUserId());
                log.warn("订单号唯一索引冲突，已回补库存: orderNo={}", orderNo);
                return;
            }
            log.info("秒杀订单创建成功: orderNo={}, 剩余库存={}", orderNo, activity.getStock() - 1);
        } catch (Exception e) {
            // 系统异常：抛出触发 RocketMQ 重试
            log.error("秒杀订单消费异常，触发 MQ 重试: orderNo={}, error={}", orderNo, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 业务失败：回补 Redis 预扣 + 落失败流水，不抛异常（避免无意义重试）。
     */
    private void handleBusinessFailure(SeckillOrderMessage message, String reason) {
        stockRedisService.rollback(message.getActivityId(), message.getUserId());
        try {
            orderMapper.insert(buildOrder(message, 2, reason));
        } catch (DuplicateKeyException ignore) {
            log.info("失败流水已存在，忽略: orderNo={}", message.getOrderNo());
        }
        log.warn("秒杀订单处理失败: orderNo={}, reason={}", message.getOrderNo(), reason);
    }

    private Order buildOrder(SeckillOrderMessage message, int status, String failReason) {
        Order order = new Order();
        order.setOrderNo(message.getOrderNo());
        order.setUserId(message.getUserId());
        order.setProductId(message.getProductId());
        order.setActivityId(message.getActivityId());
        order.setAmount(message.getAmount());
        order.setStatus(status);
        order.setFailReason(failReason);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }
}
