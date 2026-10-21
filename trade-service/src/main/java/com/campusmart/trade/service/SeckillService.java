package com.campusmart.trade.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campusmart.trade.dto.SeckillActivityCreateDTO;
import com.campusmart.trade.dto.SeckillResultVO;
import com.campusmart.trade.entity.SeckillActivity;

public interface SeckillService {

    /**
     * 秒杀入口：分布式锁防重 → Redis Lua 预扣 → 发送 MQ 异步下单。
     */
    SeckillResultVO seckill(Long userId, Long activityId);

    /**
     * 活动预热：库存写入 Redis，清空参与用户集合。
     */
    void preheat(Long activityId);

    Long createActivity(SeckillActivityCreateDTO dto);

    SeckillActivity getActivity(Long activityId);

    /**
     * 秒杀活动列表（前台展示用），按开始时间倒序。
     */
    IPage<SeckillActivity> pageActivities(Integer pageNum, Integer pageSize);
}
