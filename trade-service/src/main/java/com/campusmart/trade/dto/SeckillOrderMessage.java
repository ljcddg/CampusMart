package com.campusmart.trade.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀下单 MQ 消息体。
 */
@Data
public class SeckillOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;

    private Long userId;

    private Long activityId;

    private Long productId;

    private BigDecimal amount;

    /** 消息产生时间戳（毫秒），便于排查积压 */
    private Long timestamp;
}
