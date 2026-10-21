package com.campusmart.trade.mq;

import com.campusmart.common.constant.MqConstants;
import com.campusmart.trade.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * 秒杀下单消息生产者。同步发送保证消息落 Broker 后才返回，避免"预扣成功但消息丢失"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderProducer {

    private static final String DESTINATION = MqConstants.TOPIC_SECKILL_ORDER + ":" + MqConstants.TAG_SECKILL_ORDER;

    private final RocketMQTemplate rocketMQTemplate;

    public void send(SeckillOrderMessage message) {
        rocketMQTemplate.syncSend(DESTINATION, message, MqConstants.SEND_TIMEOUT_MILLIS);
        log.info("秒杀订单消息已发送: orderNo={}, activityId={}, userId={}",
                message.getOrderNo(), message.getActivityId(), message.getUserId());
    }
}
