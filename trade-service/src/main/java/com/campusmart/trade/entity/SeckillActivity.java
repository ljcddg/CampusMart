package com.campusmart.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动表，同时承担秒杀库存持久化职责（version 为乐观锁版本号）。
 */
@Data
@TableName("seckill_activity")
public class SeckillActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private BigDecimal seckillPrice;

    /** 秒杀库存，MySQL 侧最终一致性的兜底数据 */
    private Integer stock;

    /** 乐观锁版本号 */
    private Integer version;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0 未开始 1 进行中 2 已结束 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
