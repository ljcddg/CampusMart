package com.campusmart.trade.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campusmart.trade.dto.CreateOrderDTO;
import com.campusmart.trade.dto.OrderVO;

public interface OrderService {

    /**
     * 普通下单：查商品 → 乐观锁扣库存（远程）→ 落单，失败补偿回补库存。
     */
    OrderVO create(Long userId, CreateOrderDTO dto);

    OrderVO getByOrderNo(String orderNo);

    IPage<OrderVO> pageByUser(Long userId, Integer pageNum, Integer pageSize);
}
