package com.campusmart.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campusmart.product.dto.ProductCreateDTO;
import com.campusmart.product.dto.ProductVO;

public interface ProductService {

    /**
     * 查询商品详情。Cache-Aside 模式：先读缓存，未命中查库并回写。
     */
    ProductVO getDetail(Long productId);

    Long create(ProductCreateDTO dto, Long sellerId);

    void update(Long productId, ProductCreateDTO dto, Long sellerId);

    /**
     * 下架商品：更新状态并删除缓存，不从布隆过滤器移除。
     */
    void offShelf(Long productId, Long sellerId);

    IPage<ProductVO> page(Integer pageNum, Integer pageSize);

    /**
     * 扣减库存供交易服务内部调用（普通下单链路），走 MySQL 乐观锁。
     * 影响行数为 0 时内部重试读取最新版本，仍失败则抛库存冲突。
     */
    void deductStock(Long productId, Integer quantity);

    /**
     * 回补库存，供交易服务在下单失败时做补偿。
     */
    void restoreStock(Long productId, Integer quantity);
}
