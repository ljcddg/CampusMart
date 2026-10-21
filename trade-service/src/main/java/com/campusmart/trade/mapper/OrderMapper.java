package com.campusmart.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusmart.trade.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM `order` WHERE order_no = #{orderNo} LIMIT 1")
    Order selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM `order` WHERE user_id = #{userId} AND activity_id = #{activityId} AND status = 1 LIMIT 1")
    Order selectSuccessOrder(@Param("userId") Long userId, @Param("activityId") Long activityId);
}
