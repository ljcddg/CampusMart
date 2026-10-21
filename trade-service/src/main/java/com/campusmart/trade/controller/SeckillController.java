package com.campusmart.trade.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campusmart.common.constant.AuthConstants;
import com.campusmart.common.exception.BizException;
import com.campusmart.common.result.Result;
import com.campusmart.common.result.ResultCode;
import com.campusmart.trade.component.SeckillStockRedisService;
import com.campusmart.trade.dto.SeckillActivityCreateDTO;
import com.campusmart.trade.dto.SeckillResultVO;
import com.campusmart.trade.entity.SeckillActivity;
import com.campusmart.trade.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/trade/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;
    private final SeckillStockRedisService stockRedisService;

    /**
     * 秒杀入口。压测目标接口：1000 并发打这里。
     */
    @PostMapping("/{activityId}")
    public Result<SeckillResultVO> seckill(@PathVariable("activityId") Long activityId,
                                           @RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(seckillService.seckill(userId, activityId));
    }

    /**
     * 活动预热：库存写入 Redis。活动开始前 5 分钟调用，预热失败则不开放入口。
     */
    @PostMapping("/preheat/{activityId}")
    public Result<Void> preheat(@PathVariable("activityId") Long activityId) {
        seckillService.preheat(activityId);
        return Result.success("预热成功", null);
    }

    @PostMapping("/activity")
    public Result<Long> createActivity(@Valid @RequestBody SeckillActivityCreateDTO dto) {
        return Result.success("活动创建成功", seckillService.createActivity(dto));
    }

    @GetMapping("/activity/{activityId}")
    public Result<SeckillActivity> getActivity(@PathVariable("activityId") Long activityId) {
        return Result.success(seckillService.getActivity(activityId));
    }

    /**
     * 前台活动列表，游客可访问。
     */
    @GetMapping("/activity/list")
    public Result<IPage<SeckillActivity>> pageActivities(@RequestParam(defaultValue = "1") Integer pageNum,
                                                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(seckillService.pageActivities(pageNum, pageSize));
    }

    /**
     * 库存对账：便于压测后核对 Redis 与 MySQL 库存差值。
     */
    @GetMapping("/stock/{activityId}")
    public Result<Map<String, Object>> stock(@PathVariable("activityId") Long activityId) {
        SeckillActivity activity = seckillService.getActivity(activityId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activityId", activityId);
        data.put("mysqlStock", activity.getStock());
        data.put("redisStock", stockRedisService.getStock(activityId));
        data.put("joinedUsers", stockRedisService.getJoinedUserCount(activityId));
        return Result.success(data);
    }
}
