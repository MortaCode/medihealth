package com.myy.medihealth.cart.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CartAddVo(
        @NotBlank(message = "商品ID不能为空")
        String productId,
        @Min(value = 1, message = "数量必须大于0")
        int quantity
) {
}
