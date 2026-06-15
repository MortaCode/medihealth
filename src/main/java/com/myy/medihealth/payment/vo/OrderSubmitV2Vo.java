package com.myy.medihealth.payment.vo;

import jakarta.validation.constraints.NotBlank;

public record OrderSubmitV2Vo(@NotBlank String requestId) {
}
