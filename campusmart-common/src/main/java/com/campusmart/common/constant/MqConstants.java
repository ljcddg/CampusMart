package com.campusmart.common.constant;

/**
 * RocketMQ 主题与消费组常量。
 */
public final class MqConstants {

    private MqConstants() {
    }

    /** 秒杀订单主题 */
    public static final String TOPIC_SECKILL_ORDER = "campusmart-seckill-order";

    /** 秒杀订单标签 */
    public static final String TAG_SECKILL_ORDER = "create";

    /** 秒杀订单消费组 */
    public static final String CONSUMER_GROUP_SECKILL_ORDER = "seckill-order-consumer-group";

    /** 发送超时（毫秒） */
    public static final long SEND_TIMEOUT_MILLIS = 3000L;
}
