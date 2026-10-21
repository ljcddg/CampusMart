package com.campusmart.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campusmart.common.constant.AuthConstants;
import com.campusmart.common.exception.BizException;
import com.campusmart.common.result.Result;
import com.campusmart.common.result.ResultCode;
import com.campusmart.product.dto.ProductCreateDTO;
import com.campusmart.product.dto.ProductVO;
import com.campusmart.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 商品详情：缓存命中链路，秒杀/详情页高频入口。
     */
    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable("id") Long id) {
        return Result.success(productService.getDetail(id));
    }

    @GetMapping("/page")
    public Result<IPage<ProductVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(productService.page(pageNum, pageSize));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProductCreateDTO dto,
                               @RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long sellerId) {
        requireLogin(sellerId);
        return Result.success("发布成功", productService.create(dto, sellerId));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody ProductCreateDTO dto,
                               @RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long sellerId) {
        requireLogin(sellerId);
        productService.update(id, dto, sellerId);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> offShelf(@PathVariable("id") Long id,
                                 @RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long sellerId) {
        requireLogin(sellerId);
        productService.offShelf(id, sellerId);
        return Result.success("已下架", null);
    }

    /**
     * 内部接口：交易服务普通下单时扣减库存（MySQL 乐观锁）。
     */
    @PostMapping("/{id}/deduct")
    public Result<Void> deduct(@PathVariable("id") Long id,
                               @RequestParam(value = "quantity", defaultValue = "1") Integer quantity) {
        productService.deductStock(id, quantity);
        return Result.success("扣减成功", null);
    }

    /**
     * 内部接口：交易服务下单失败时回补库存。
     */
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable("id") Long id,
                                @RequestParam(value = "quantity", defaultValue = "1") Integer quantity) {
        productService.restoreStock(id, quantity);
        return Result.success("回补成功", null);
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }
}