package com.campusmart.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillResultVO {

    /** 预生成的订单号，用户可凭此轮询订单状态 */
    private String orderNo;

    private Long activityId;

    /** 固定为“处理中”，订单由 MQ 异步落库 */
    private String status;

    private String tip;
}
