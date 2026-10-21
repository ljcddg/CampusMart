package com.campusmart.trade.feign;

import com.campusmart.common.result.Result;
import com.campusmart.trade.dto.ProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品服务远程调用。仅普通下单链路使用；秒杀链路刻意不调用，库存在预热阶段已进入 Redis。
 */
@FeignClient(name = "product-service")
public interface ProductFeignClient {

    @GetMapping("/product/{id}")
    Result<ProductDTO> detail(@PathVariable("id") Long id);

    @PostMapping("/product/{id}/deduct")
    Result<Void> deduct(@PathVariable("id") Long id, @RequestParam("quantity") Integer quantity);

    @PostMapping("/product/{id}/restore")
    Result<Void> restore(@PathVariable("id") Long id, @RequestParam("quantity") Integer quantity);
}
