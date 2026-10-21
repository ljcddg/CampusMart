package com.campusmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品表。
 */
@Data
@TableName("product")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private BigDecimal price;

    private Integer stock;

    /** 乐观锁版本号，普通下单扣库存时使用 */
    private Integer version;

    /** 0 下架 1 在售 */
    private Integer status;

    private Long sellerId;

    private String coverImage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
