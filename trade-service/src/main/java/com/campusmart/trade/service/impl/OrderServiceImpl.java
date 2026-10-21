package com.campusmart.trade.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusmart.common.exception.BizException;
import com.campusmart.common.result.Result;
import com.campusmart.common.result.ResultCode;
import com.campusmart.trade.dto.CreateOrderDTO;
import com.campusmart.trade.dto.OrderVO;
import com.campusmart.trade.dto.ProductDTO;
import com.campusmart.trade.entity.Order;
import com.campusmart.trade.feign.ProductFeignClient;
import com.campusmart.trade.mapper.OrderMapper;
import com.campusmart.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 普通交易链路。
 *
 * <p>与秒杀链路的区别：走同步下单，商品库存由 product-service 用 MySQL 乐观锁扣减。
 * 跨服务无分布式事务（未引入 Seata），采用「先扣库存、落单失败则补偿回补」的最终一致方案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductFeignClient productFeignClient;

    @Override
    public OrderVO create(Long userId, CreateOrderDTO dto) {
        int quantity = (dto.getQuantity() == null || dto.getQuantity() <= 0) ? 1 : dto.getQuantity();

        // 1. 查询商品（远程）
        ProductDTO product = fetchProduct(dto.getProductId());
        if (product.getStatus() != null && product.getStatus() == 0) {
            throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
        }
        if (product.getStock() == null || product.getStock() < quantity) {
            throw new BizException(ResultCode.PRODUCT_STOCK_NOT_ENOUGH);
        }

        // 2. 扣减库存（product-service 内部乐观锁 + 重试）
        Result<Void> deductResult = productFeignClient.deduct(product.getId(), quantity);
        if (deductResult == null || !ResultCode.SUCCESS.getCode().equals(deductResult.getCode())) {
            String message = deductResult == null ? "库存扣减失败" : deductResult.getMessage();
            throw new BizException(message);
        }

        // 3. 落单，失败则补偿回补库存
        try {
            Order order = new Order();
            order.setOrderNo(String.valueOf(IdWorker.getId()));
            order.setUserId(userId);
            order.setProductId(product.getId());
            order.setActivityId(null);
            order.setAmount(product.getPrice().multiply(java.math.BigDecimal.valueOf(quantity)));
            order.setStatus(1);
            order.setCreateTime(LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.insert(order);
            log.info("普通订单创建成功: orderNo={}, productId={}, quantity={}", order.getOrderNo(), product.getId(), quantity);
            return toVO(order);
        } catch (Exception e) {
            log.error("订单落库失败，开始补偿回补库存: productId={}, quantity={}, error={}",
                    product.getId(), quantity, e.getMessage());
            try {
                productFeignClient.restore(product.getId(), quantity);
            } catch (Exception ex) {
                log.error("库存补偿失败，需人工介入: productId={}, quantity={}", product.getId(), quantity);
            }
            throw new BizException("下单失败，请重试");
        }
    }

    @Override
    public OrderVO getByOrderNo(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST);
        }
        return toVO(order);
    }

    @Override
    public IPage<OrderVO> pageByUser(Long userId, Integer pageNum, Integer pageSize) {
        Page<Order> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        return orderMapper.selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Order>()
                        .eq("user_id", userId)
                        .orderByDesc("create_time"))
                .convert(this::toVO);
    }

    private ProductDTO fetchProduct(Long productId) {
        Result<ProductDTO> result;
        try {
            result = productFeignClient.detail(productId);
        } catch (Exception e) {
            log.error("调用商品服务失败: productId={}, error={}", productId, e.getMessage());
            throw new BizException("商品服务暂不可用，请稍后重试");
        }
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_EXIST);
        }
        return result.getData();
    }

    private OrderVO toVO(Order order) {
        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .productId(order.getProductId())
                .activityId(order.getActivityId())
                .amount(order.getAmount())
                .status(order.getStatus())
                .failReason(order.getFailReason())
                .createTime(order.getCreateTime())
                .build();
    }
}
