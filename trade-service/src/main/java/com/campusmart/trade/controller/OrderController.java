package com.campusmart.trade.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campusmart.common.constant.AuthConstants;
import com.campusmart.common.exception.BizException;
import com.campusmart.common.result.Result;
import com.campusmart.common.result.ResultCode;
import com.campusmart.trade.dto.CreateOrderDTO;
import com.campusmart.trade.dto.OrderVO;
import com.campusmart.trade.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trade/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody CreateOrderDTO dto,
                                  @RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long userId) {
        requireLogin(userId);
        return Result.success("下单成功", orderService.create(userId, dto));
    }

    /**
     * 秒杀下单是异步的，前端拿 orderNo 轮询该接口查订单状态。
     */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable("orderNo") String orderNo) {
        return Result.success(orderService.getByOrderNo(orderNo));
    }

    @GetMapping("/page")
    public Result<IPage<OrderVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                       @RequestParam(defaultValue = "10") Integer pageSize,
                                       @RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long userId) {
        requireLogin(userId);
        return Result.success(orderService.pageByUser(userId, pageNum, pageSize));
    }

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }
}
