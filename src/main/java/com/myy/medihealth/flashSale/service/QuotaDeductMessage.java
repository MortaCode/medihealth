package com.myy.medihealth.flashSale.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 名额扣减消息 — RabbitMQ 消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotaDeductMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 名额编号 */
    private String quotaId;

    /** 请求时间戳 */
    private long requestTime;

    /** 已重试次数 */
    private int retryCount;

    public QuotaDeductMessage(String userId, String quotaId) {
        this.userId = userId;
        this.quotaId = quotaId;
        this.requestTime = System.currentTimeMillis();
        this.retryCount = 0;
    }

    public QuotaDeductMessage incRetry() {
        this.retryCount++;
        return this;
    }
}
