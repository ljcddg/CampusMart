-- 秒杀库存预扣脚本（第一道防线）
-- KEYS[1]: seckill:stock:{activityId}  预扣库存
-- KEYS[2]: seckill:users:{activityId}  已参与用户集合
-- ARGV[1]: userId
-- ARGV[2]: 用户集合 TTL（秒），活动结束后自动清理
-- 返回值：
--   -3 : 库存 key 不存在（活动未预热或已过期）
--   -1 : 库存不足
--   -2 : 用户重复参与
--  >=0 : 预扣成功，返回预扣后的剩余库存
local stockStr = redis.call('GET', KEYS[1])
if not stockStr then
    return -3
end

local stock = tonumber(stockStr)
if stock <= 0 then
    return -1
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
-- 集合首次 SADD 时才创建，必须在写入后补 TTL，否则永久驻留
redis.call('EXPIRE', KEYS[2], ARGV[2])
return stock - 1
