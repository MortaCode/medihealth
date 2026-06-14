package com.myy.medihealth.product.vo;

import java.math.BigDecimal;

public record ProductSaveVo(
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String image,
        String images,
        String category,
        Integer status,
        Integer prescriptionRequired
) {
}
