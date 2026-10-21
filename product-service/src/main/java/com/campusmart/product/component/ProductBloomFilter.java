package com.campusmart.product.component;

import com.campusmart.common.constant.RedisKeys;
import com.campusmart.product.mapper.ProductMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品 ID 布隆过滤器。
 *
 * <p>作用：在缓存未命中与查库之间加一层，拦截不存在商品的查询请求，防止缓存穿透把压力打到 MySQL。
 *
 * <p>设计约束：
 * <ul>
 *   <li>容量按预估商品总量设置，误判率 0.01；</li>
 *   <li>商品创建时写入；下架不删除（Redisson 布隆过滤器不支持删除），由缓存 TTL 与数据库 status 字段兜底；</li>
 *   <li>Redis 或 DB 异常时降级为放行（返回 true），避免误杀正常请求。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductBloomFilter {

    /** 预估商品总量 */
    private static final long EXPECTED_INSERTIONS = 100_000L;

    /** 误判率 */
    private static final double FALSE_POSITIVE_PROBABILITY = 0.01D;

    private final RedissonClient redissonClient;
    private final ProductMapper productMapper;

    private RBloomFilter<Long> bloomFilter;

    @PostConstruct
    public void init() {
        try {
            bloomFilter = redissonClient.getBloomFilter(RedisKeys.BLOOM_PRODUCT_IDS);
            boolean created = bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_POSITIVE_PROBABILITY);
            if (created) {
                warmUp();
            } else {
                log.info("布隆过滤器已存在，沿用 Redis 中的存量位图: {}", RedisKeys.BLOOM_PRODUCT_IDS);
            }
        } catch (Exception e) {
            log.error("布隆过滤器初始化失败，降级为直接查库: {}", e.getMessage());
            bloomFilter = null;
        }
    }

    /**
     * 首次创建时用数据库存量商品 ID 预热。
     */
    private void warmUp() {
        List<Long> ids = productMapper.selectAllIds();
        ids.forEach(bloomFilter::add);
        log.info("布隆过滤器预热完成: 容量={}, 误判率={}, 写入商品数={}", EXPECTED_INSERTIONS, FALSE_POSITIVE_PROBABILITY, ids.size());
    }

    /**
     * 判断商品 ID 是否可能存在。false 表示一定不存在，可直接拒绝；true 表示可能存在，需继续查库。
     */
    public boolean contains(Long productId) {
        if (bloomFilter == null || productId == null) {
            return true;
        }
        try {
            return bloomFilter.contains(productId);
        } catch (Exception e) {
            log.warn("布隆过滤器查询异常，降级放行: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 新增商品时写入过滤器。
     */
    public void add(Long productId) {
        if (bloomFilter == null || productId == null) {
            return;
        }
        try {
            bloomFilter.add(productId);
        } catch (Exception e) {
            log.warn("布隆过滤器写入失败: productId={}, error={}", productId, e.getMessage());
        }
    }
}
