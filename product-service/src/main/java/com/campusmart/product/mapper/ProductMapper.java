package com.campusmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusmart.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 乐观锁扣减库存。影响行数为 0 表示版本冲突或库存不足。
     */
    @Update("UPDATE product SET stock = stock - #{qty}, version = version + 1, update_time = NOW() "
            + "WHERE id = #{id} AND stock >= #{qty} AND version = #{version}")
    int deductStock(@Param("id") Long id, @Param("qty") Integer qty, @Param("version") Integer version);

    /**
     * 回补库存，用于交易服务下单失败后的补偿。
     */
    @Update("UPDATE product SET stock = stock + #{qty}, version = version + 1, update_time = NOW() WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("qty") Integer qty);

    /**
     * 查询全部商品 ID，用于布隆过滤器预热。
     */
    @Select("SELECT id FROM product")
    List<Long> selectAllIds();
}
