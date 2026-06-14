package com.myy.medihealth.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_medical_product")
public class Product {

    @TableId
    private String id;

    private String name;

    private String description;

    private BigDecimal price;

    private Integer stock;

    private String image;

    private String images;

    private String category;

    private Integer status;

    private Integer sales;

    private Integer prescriptionRequired;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
