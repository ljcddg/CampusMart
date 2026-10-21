package com.campusmart.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusmart.trade.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /**
     * 秒杀库存乐观锁扣减（第二道防线）。
     * 影响行数为 0 表示库存不足或版本冲突，需回补 Redis 预扣。
     */
    @Update("UPDATE seckill_activity SET stock = stock - 1, version = version + 1, update_time = NOW() "
            + "WHERE id = #{activityId} AND stock > 0 AND version = #{version}")
    int deductStock(@Param("activityId") Long activityId, @Param("version") Integer version);

    /**
     * 回补 MySQL 库存，用于重复消息导致的多扣补偿。
     */
    @Update("UPDATE seckill_activity SET stock = stock + 1, version = version + 1, update_time = NOW() WHERE id = #{activityId}")
    int restoreStock(@Param("activityId") Long activityId);
}
