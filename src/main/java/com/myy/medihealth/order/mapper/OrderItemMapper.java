package com.myy.medihealth.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myy.medihealth.order.entity.OrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderItemMapper extends BaseMapper<OrderItem> {

    int batchInsert(@Param("list") List<OrderItem> list);
}
