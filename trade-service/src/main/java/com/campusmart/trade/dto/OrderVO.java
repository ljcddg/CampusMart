package com.campusmart.trade.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long productId;

    private Long activityId;

    private BigDecimal amount;

    private Integer status;

    private String failReason;

    private LocalDateTime createTime;
}
