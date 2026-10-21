package com.campusmart.trade.component;

import com.campusmart.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 秒杀库存 Redis 操作封装。
 *
 * <p>第一道防线：Lua 脚本在 Redis 单线程内原子完成「校验库存 → 校验是否重复 → 扣减 → 记录用户」，
 * 避免并发下的检查与扣减之间的竞态，保证库存不会被扣成负数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStockRedisService {

    /** Lua 返回值：库存 key 不存在 */
    public static final long RESULT_NO_STOCK_KEY = -3L;
    /** Lua 返回值：库存不足 */
    public static final long RESULT_SOLD_OUT = -1L;
    /** Lua 返回值：重复参与 */
    public static final long RESULT_REPEAT = -2L;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill_stock.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    /**
     * 执行预扣。返回 >=0 表示成功并给出剩余库存，负数为失败原因。
     */
    public long tryDeduct(Long activityId, Long userId) {
        long ttlSeconds = Duration.ofHours(RedisKeys.SECKILL_KEY_TTL_HOURS).toSeconds();
        Long result = redisTemplate.execute(SECKILL_SCRIPT,
                List.of(RedisKeys.seckillStock(activityId), RedisKeys.seckillUsers(activityId)),
                String.valueOf(userId),
                String.valueOf(ttlSeconds));
        if (result == null) {
            log.warn("秒杀脚本返回空结果，按库存 key 不存在处理: activityId={}", activityId);
            return RESULT_NO_STOCK_KEY;
        }
        return result;
    }

    /**
     * 回补预扣：Redis INCR 库存并移除用户占位。
     * 方案只要求回补库存；同时移除用户占位是必要的，否则该用户后续合法请求会被误判为重复参与。
     */
    public void rollback(Long activityId, Long userId) {
        try {
            redisTemplate.opsForValue().increment(RedisKeys.seckillStock(activityId));
            redisTemplate.opsForSet().remove(RedisKeys.seckillUsers(activityId), String.valueOf(userId));
            log.info("已回补 Redis 预扣库存: activityId={}, userId={}", activityId, userId);
        } catch (Exception e) {
            log.error("回补 Redis 库存失败，需人工核对: activityId={}, userId={}, error={}",
                    activityId, userId, e.getMessage());
        }
    }

    /**
     * 活动预热：写入库存、清空已参与用户集合。
     */
    public void preheat(Long activityId, Integer stock, Duration ttl) {
        redisTemplate.opsForValue().set(RedisKeys.seckillStock(activityId), String.valueOf(stock), ttl);
        redisTemplate.delete(RedisKeys.seckillUsers(activityId));
        log.info("秒杀活动预热完成: activityId={}, stock={}, ttl={}s", activityId, stock, ttl.toSeconds());
    }

    public Integer getStock(Long activityId) {
        String value = redisTemplate.opsForValue().get(RedisKeys.seckillStock(activityId));
        return value == null ? null : Integer.valueOf(value);
    }

    public Long getJoinedUserCount(Long activityId) {
        return redisTemplate.opsForSet().size(RedisKeys.seckillUsers(activityId));
    }
}
