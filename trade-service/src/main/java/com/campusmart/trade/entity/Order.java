package com.campusmart.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表。order 是 MySQL 关键字，表名需反引号。
 */
@Data
@TableName("`order`")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号，唯一索引，同时作为 MQ 消费幂等键 */
    private String orderNo;

    private Long userId;

    private Long productId;

    /** 秒杀活动 ID，普通订单为 null */
    private Long activityId;

    private BigDecimal amount;

    /** 0 处理中 1 成功 2 失败 */
    private Integer status;

    /** 失败原因，便于排查秒杀链路回补场景 */
    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
