package com.myy.medihealth.cart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_cart_item")
public class CartItem {

    @TableId
    private String id;

    private String userId;

    private String productId;

    /** 所属店铺 ID（加购时自动关联） */
    private String storeId;

    /** 发货仓库 ID（加购时自动匹配就近仓） */
    private String warehouseId;

    private Integer quantity;

    private Integer selected;

    private LocalDateTime createTime;
}
