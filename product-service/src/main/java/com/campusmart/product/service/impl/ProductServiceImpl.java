package com.campusmart.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusmart.common.constant.RedisKeys;
import com.campusmart.common.exception.BizException;
import com.campusmart.common.result.ResultCode;
import com.campusmart.product.component.ProductBloomFilter;
import com.campusmart.product.dto.ProductCreateDTO;
import com.campusmart.product.dto.ProductVO;
import com.campusmart.product.entity.Product;
import com.campusmart.product.mapper.ProductMapper;
import com.campusmart.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品服务实现。
 *
 * <p>缓存策略（Cache-Aside）：
 * <ol>
 *   <li>读：Redis 命中直接返回；未命中先过布隆过滤器，判不存在直接 404；判可能存在则查库并回写缓存；</li>
 *   <li>写：更新 MySQL 后删除 Redis key，不做延迟双删（详情允许秒级不一致）；</li>
 *   <li>TTL：30 分钟加 0~300 秒随机值，打散过期时间，防止集中失效造成缓存雪崩。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** 乐观锁冲突重试次数 */
    private static final int STOCK_RETRY_TIMES = 3;

    private final ProductMapper productMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductBloomFilter productBloomFilter;

    @Override
    public ProductVO getDetail(Long productId) {
        long start = System.currentTimeMillis();
        String cacheKey = RedisKeys.productDetail(productId);

        // 1. 查缓存
        String cached = safeGet(cacheKey);
        if (StringUtils.hasText(cached)) {
            ProductVO cachedVo = deserialize(cached);
            if (cachedVo != null) {
                log.debug("商品详情命中缓存: productId={}, cost={}ms", productId, System.currentTimeMillis() - start);
                return cachedVo;
            }
        }

        // 2. 布隆过滤器拦截缓存穿透：判不存在直接返回，不查库
        if (!productBloomFilter.contains(productId)) {
            log.debug("布隆过滤器拦截非法商品查询: productId={}", productId);
            throw new BizException(ResultCode.PRODUCT_NOT_EXIST);
        }

        // 3. 查库
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_EXIST);
        }

        // 4. 回写缓存
        ProductVO vo = toVO(product);
        writeCache(cacheKey, vo);
        log.debug("商品详情回源查库: productId={}, cost={}ms", productId, System.currentTimeMillis() - start);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductCreateDTO dto, Long sellerId) {
        Product product = new Product();
        product.setTitle(dto.getTitle());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setVersion(0);
        product.setStatus(1);
        product.setSellerId(sellerId);
        product.setCoverImage(dto.getCoverImage());
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.insert(product);

        // 商品创建时写入布隆过滤器
        productBloomFilter.add(product.getId());
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long productId, ProductCreateDTO dto, Long sellerId) {
        Product product = requireOwnedProduct(productId, sellerId);
        product.setTitle(dto.getTitle());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setCoverImage(dto.getCoverImage());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);

        // 先更新数据库，再删除缓存
        deleteCache(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offShelf(Long productId, Long sellerId) {
        Product product = requireOwnedProduct(productId, sellerId);
        product.setStatus(0);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);

        // 下架不移除布隆过滤器（不支持删除），靠缓存删除 + status 字段兜底
        deleteCache(productId);
    }

    @Override
    public IPage<ProductVO> page(Integer pageNum, Integer pageSize) {
        Page<Product> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        return productMapper.selectPage(page, null).convert(this::toVO);
    }

    /**
     * 乐观锁扣库存。冲突时重新读取最新 version 重试，超过上限抛冲突异常。
     * 不加事务：每次重试都需要读到最新的 version，长事务会导致读到快照旧值。
     */
    @Override
    public void deductStock(Long productId, Integer quantity) {
        int qty = (quantity == null || quantity <= 0) ? 1 : quantity;
        for (int i = 0; i < STOCK_RETRY_TIMES; i++) {
            Product product = productMapper.selectById(productId);
            if (product == null) {
                throw new BizException(ResultCode.PRODUCT_NOT_EXIST);
            }
            if (product.getStatus() != null && product.getStatus() == 0) {
                throw new BizException(ResultCode.PRODUCT_OFF_SHELF);
            }
            if (product.getStock() == null || product.getStock() < qty) {
                throw new BizException(ResultCode.PRODUCT_STOCK_NOT_ENOUGH);
            }
            int rows = productMapper.deductStock(productId, qty, product.getVersion());
            if (rows > 0) {
                // 库存变更后删除缓存，保证详情页看到最新库存
                deleteCache(productId);
                return;
            }
            log.debug("乐观锁冲突，重试第 {} 次: productId={}", i + 1, productId);
        }
        throw new BizException(ResultCode.PRODUCT_STOCK_CONFLICT);
    }

    @Override
    public void restoreStock(Long productId, Integer quantity) {
        int qty = (quantity == null || quantity <= 0) ? 1 : quantity;
        productMapper.restoreStock(productId, qty);
        deleteCache(productId);
        log.info("库存已回补: productId={}, quantity={}", productId, qty);
    }

    private Product requireOwnedProduct(Long productId, Long sellerId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.PRODUCT_NOT_EXIST);
        }
        if (sellerId != null && !sellerId.equals(product.getSellerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return product;
    }

    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读取缓存失败，降级查库: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, ProductVO vo) {
        try {
            long ttlSeconds = RedisKeys.PRODUCT_DETAIL_TTL_MINUTES * 60
                    + ThreadLocalRandom.current().nextLong(RedisKeys.PRODUCT_DETAIL_TTL_RANDOM_SECONDS + 1);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(vo), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("写入缓存失败: key={}, error={}", key, e.getMessage());
        }
    }

    private void deleteCache(Long productId) {
        try {
            redisTemplate.delete(RedisKeys.productDetail(productId));
        } catch (Exception e) {
            log.warn("删除缓存失败: productId={}, error={}", productId, e.getMessage());
        }
    }

    private ProductVO deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProductVO.class);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，按未命中处理: {}", e.getMessage());
            return null;
        }
    }

    private ProductVO toVO(Product product) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setTitle(product.getTitle());
        vo.setDescription(product.getDescription());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setStatus(product.getStatus());
        vo.setSellerId(product.getSellerId());
        vo.setCoverImage(product.getCoverImage());
        vo.setCreateTime(product.getCreateTime());
        return vo;
    }
}
