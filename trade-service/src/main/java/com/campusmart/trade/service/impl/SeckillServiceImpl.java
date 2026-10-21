package com.campusmart.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusmart.common.constant.RedisKeys;
import com.campusmart.common.exception.BizException;
import com.campusmart.common.result.ResultCode;
import com.campusmart.trade.component.SeckillStockRedisService;
import com.campusmart.trade.dto.SeckillActivityCreateDTO;
import com.campusmart.trade.dto.SeckillOrderMessage;
import com.campusmart.trade.dto.SeckillResultVO;
import com.campusmart.trade.entity.SeckillActivity;
import com.campusmart.trade.mapper.SeckillActivityMapper;
import com.campusmart.trade.mq.SeckillOrderProducer;
import com.campusmart.trade.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀核心链路。
 *
 * <p>执行顺序（与设计一致）：
 * <ol>
 *   <li>Redisson tryLock(0, 10s) 拦截同一用户重复提交，不承担防超卖职责；</li>
 *   <li>Redis Lua 原子预扣，拦截无效流量，保护 MySQL；</li>
 *   <li>预生成订单号并发送 RocketMQ，用户侧只等待 Redis + MQ send；</li>
 *   <li>订单落库与 MySQL 乐观锁扣减由消费者完成。</li>
 * </ol>
 *
 * <p>注意：秒杀链路不同步调用 product-service，库存已在预热阶段写入 Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    /** 锁租期：秒杀链路本身是毫秒级操作，10 秒足够，异常时自动释放 */
    private static final long LOCK_LEASE_SECONDS = 10L;

    private final RedissonClient redissonClient;
    private final SeckillStockRedisService stockRedisService;
    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderProducer seckillOrderProducer;

    @Override
    public SeckillResultVO seckill(Long userId, Long activityId) {
        RLock lock = redissonClient.getLock(RedisKeys.seckillLock(userId, activityId));
        boolean locked = false;
        try {
            // 1. 防重复提交：锁粒度为用户+活动，不同用户之间无竞争
            locked = lock.tryLock(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException(ResultCode.SECKILL_SUBMIT_TOO_FAST);
            }

            // 2. Redis 预扣（第一道防线）
            long result = stockRedisService.tryDeduct(activityId, userId);
            if (result == SeckillStockRedisService.RESULT_SOLD_OUT) {
                throw new BizException(ResultCode.SECKILL_SOLD_OUT);
            }
            if (result == SeckillStockRedisService.RESULT_REPEAT) {
                throw new BizException(ResultCode.SECKILL_REPEAT);
            }
            if (result == SeckillStockRedisService.RESULT_NO_STOCK_KEY) {
                throw new BizException(resolveActivityState(activityId));
            }

            // 3. 仅预扣成功的请求才回查一次活动信息，量级等于库存数，不会成为热点
            SeckillActivity activity = activityMapper.selectById(activityId);
            if (activity == null) {
                stockRedisService.rollback(activityId, userId);
                throw new BizException(ResultCode.SECKILL_NOT_EXIST);
            }

            // 4. 预生成订单号 + 发送 MQ
            String orderNo = String.valueOf(IdWorker.getId());
            SeckillOrderMessage message = new SeckillOrderMessage();
            message.setOrderNo(orderNo);
            message.setUserId(userId);
            message.setActivityId(activityId);
            message.setProductId(activity.getProductId());
            message.setAmount(activity.getSeckillPrice());
            message.setTimestamp(System.currentTimeMillis());

            try {
                seckillOrderProducer.send(message);
            } catch (Exception e) {
                // 消息发送失败必须回补预扣，否则库存被"吃掉"
                stockRedisService.rollback(activityId, userId);
                log.error("秒杀消息发送失败，已回补库存: activityId={}, userId={}, error={}",
                        activityId, userId, e.getMessage());
                throw new BizException("下单繁忙，请稍后重试");
            }

            log.info("秒杀预扣成功: activityId={}, userId={}, orderNo={}, 剩余库存={}",
                    activityId, userId, orderNo, result);
            return new SeckillResultVO(orderNo, activityId, "处理中", "抢购成功，订单处理中");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("系统繁忙，请稍后重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("释放秒杀锁失败: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void preheat(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.SECKILL_NOT_EXIST);
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            throw new BizException(ResultCode.SECKILL_ENDED);
        }
        if (activity.getStock() == null || activity.getStock() <= 0) {
            throw new BizException("活动库存为 0，无法预热");
        }

        // TTL = 剩余活动时长 + 结束后保留 1 小时
        Duration ttl = Duration.between(now, activity.getEndTime())
                .plusHours(RedisKeys.SECKILL_KEY_TTL_HOURS);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofHours(RedisKeys.SECKILL_KEY_TTL_HOURS);
        }
        stockRedisService.preheat(activityId, activity.getStock(), ttl);
    }

    @Override
    public Long createActivity(SeckillActivityCreateDTO dto) {
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BizException("结束时间必须晚于开始时间");
        }
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(dto.getProductId());
        activity.setSeckillPrice(dto.getSeckillPrice());
        activity.setStock(dto.getStock());
        activity.setVersion(0);
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setStatus(resolveStatusByTime(dto.getStartTime(), dto.getEndTime()));
        activity.setCreateTime(LocalDateTime.now());
        activity.setUpdateTime(LocalDateTime.now());
        activityMapper.insert(activity);
        return activity.getId();
    }

    @Override
    public SeckillActivity getActivity(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException(ResultCode.SECKILL_NOT_EXIST);
        }
        return activity;
    }

    @Override
    public IPage<SeckillActivity> pageActivities(Integer pageNum, Integer pageSize) {
        Page<SeckillActivity> page = new Page<>(
                pageNum == null || pageNum < 1 ? 1 : pageNum,
                pageSize == null || pageSize < 1 ? 10 : pageSize);
        return activityMapper.selectPage(page,
                new LambdaQueryWrapper<SeckillActivity>().orderByDesc(SeckillActivity::getStartTime));
    }

    /**
     * Redis 库存 key 缺失时，回查数据库给出准确原因（未开始 / 已结束 / 已售罄）。
     */    private ResultCode resolveActivityState(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            return ResultCode.SECKILL_NOT_EXIST;
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            return ResultCode.SECKILL_NOT_START;
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            return ResultCode.SECKILL_ENDED;
        }
        return ResultCode.SECKILL_SOLD_OUT;
    }

    private Integer resolveStatusByTime(LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(start)) {
            return 0;
        }
        return now.isAfter(end) ? 2 : 1;
    }
}
