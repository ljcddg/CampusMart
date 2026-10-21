package com.campusmart.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品详情视图对象，同时作为 Redis 缓存的序列化载体。
 */
@Data
public class ProductVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String description;

    private BigDecimal price;

    private Integer stock;

    private Integer status;

    private Long sellerId;

    private String coverImage;

    private LocalDateTime createTime;
}
