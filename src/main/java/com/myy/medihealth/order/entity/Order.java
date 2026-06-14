package com.myy.medihealth.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {

    @TableId
    private String id;

    private String orderId;

    private String userId;

    private BigDecimal amount;

    private Integer status;

    @Version
    private Integer version;

    private Integer prescriptionFlag;

    private LocalDateTime payTime;

    private LocalDateTime expireTime;

    private Integer delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
