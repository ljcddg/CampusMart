package com.campusmart.common.constant;

/**
 * Redis Key 统一规划。集中管理避免各服务各写一套导致 key 冲突。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 商品详情缓存：product:detail:{id} */
    public static final String PRODUCT_DETAIL_PREFIX = "product:detail:";

    /** 商品 ID 布隆过滤器 */
    public static final String BLOOM_PRODUCT_IDS = "bloom:product:ids";

    /** 秒杀库存：seckill:stock:{activityId} */
    public static final String SECKILL_STOCK_PREFIX = "seckill:stock:";

    /** 已参与用户集合：seckill:users:{activityId} */
    public static final String SECKILL_USERS_PREFIX = "seckill:users:";

    /** 防重复提交锁：seckill:lock:{userId}:{activityId} */
    public static final String SECKILL_LOCK_PREFIX = "seckill:lock:";

    /** 商品缓存 TTL：30 分钟 */
    public static final long PRODUCT_DETAIL_TTL_MINUTES = 30L;

    /** 商品缓存随机 TTL 上限：300 秒，用于打散过期时间防缓存雪崩 */
    public static final long PRODUCT_DETAIL_TTL_RANDOM_SECONDS = 300L;

    /** 秒杀相关 key 在活动结束后的保留时长：1 小时 */
    public static final long SECKILL_KEY_TTL_HOURS = 1L;

    public static String productDetail(Long productId) {
        return PRODUCT_DETAIL_PREFIX + productId;
    }

    public static String seckillStock(Long activityId) {
        return SECKILL_STOCK_PREFIX + activityId;
    }

    public static String seckillUsers(Long activityId) {
        return SECKILL_USERS_PREFIX + activityId;
    }

    public static String seckillLock(Long userId, Long activityId) {
        return SECKILL_LOCK_PREFIX + userId + ":" + activityId;
    }
}
