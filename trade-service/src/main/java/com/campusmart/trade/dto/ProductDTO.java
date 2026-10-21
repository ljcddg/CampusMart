package com.campusmart.trade.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品信息，Feign 调用 product-service 的返回载体。
 */
@Data
public class ProductDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private BigDecimal price;

    private Integer stock;

    private Integer status;
}
