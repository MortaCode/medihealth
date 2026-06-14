package com.myy.medihealth.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myy.medihealth.cart.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface CartItemMapper extends BaseMapper<CartItem> {

    @Delete("DELETE FROM t_cart_item WHERE user_id = #{userId} AND selected = 1")
    int deleteSelectedByUserId(@Param("userId") String userId);
}
