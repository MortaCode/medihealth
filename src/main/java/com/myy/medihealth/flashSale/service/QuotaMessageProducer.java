package com.myy.medihealth.flashSale.service;

import com.myy.medihealth.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 名额扣减消息生产者
 *
 * 当同步 DB 乐观锁 3 次重试全失败时，发送异步补偿消息，
 * 由消费者继续重试，保证最终一致性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送异步持久化补偿消息
     */
    public void sendDeductMessage(QuotaDeductMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.MAIN_EXCHANGE,
                RabbitMQConfig.MAIN_ROUTING_KEY,
                message
        );
        log.info("已发送名额扣减补偿消息 userId={}, quotaId={}, retryCount={}",
                message.getUserId(), message.getQuotaId(), message.getRetryCount());
    }

    /**
     * 发送到死信队列（消费者最终重试失败后调用）
     */
    public void sendToDlq(QuotaDeductMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DLX_EXCHANGE,
                RabbitMQConfig.DLX_ROUTING_KEY,
                message
        );
        log.error("名额扣减最终失败，已入死信队列 userId={}, quotaId={}",
                message.getUserId(), message.getQuotaId());
    }
}
