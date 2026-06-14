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

    private Integer quantity;

    private Integer selected;

    private LocalDateTime createTime;
}
