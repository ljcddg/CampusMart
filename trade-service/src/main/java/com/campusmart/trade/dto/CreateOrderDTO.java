package com.campusmart.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderDTO {

    @NotNull(message = "商品 ID 不能为空")
    private Long productId;

    @Min(value = 1, message = "购买数量至少为 1")
    private Integer quantity = 1;
}
