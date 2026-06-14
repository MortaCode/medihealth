package com.myy.medihealth.flashSale.service;

import com.myy.medihealth.flashSale.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 名额扣减消费者 — MQ 异步重试 DB 持久化
 *
 * 重试策略：
 *   1. 消费消息 → 尝试 DB 乐观锁落库
 *   2. 成功 → ACK
 *   3. 失败 + retryCount < MAX_RETRIES → 递增计数器，重新发送消息（延迟重试），ACK 旧消息
 *   4. 失败 + retryCount >= MAX_RETRIES → REJECT → DLQ + Redis 回滚
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaMessageConsumer {

    private static final int MAX_RETRIES = 10;

    private final DatabaseUpdateService databaseUpdateService;
    private final RedisPreDeductService redisPreDeductService;
    private final QuotaMessageProducer producer;

    @RabbitListener(queues = RabbitMQConfig.QUEUE, ackMode = "MANUAL")
    public void handleDeductMessage(QuotaDeductMessage message,
                                     Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("处理名额扣减消息 userId={}, quotaId={}, retry={}/{}",
                message.getUserId(), message.getQuotaId(), message.getRetryCount(), MAX_RETRIES);

        try {
            boolean success = databaseUpdateService.deductQuotaWithOptimisticLock(message.getQuotaId());

            if (success) {
                channel.basicAck(deliveryTag, false);
                log.info("名额扣减异步落库成功 userId={}, quotaId={}",
                        message.getUserId(), message.getQuotaId());
                return;
            }
            // 乐观锁冲突 → 判断是否继续重试
            if (message.getRetryCount() < MAX_RETRIES) {
                // 递增重试计数，重新发送，确认旧消息
                message.incRetry();
                producer.sendDeductMessage(message);
                channel.basicAck(deliveryTag, false);
                log.warn("名额扣减重试中 userId={}, retry={}",
                        message.getUserId(), message.getRetryCount());
            } else {
                // 超过最大重试 → REJECT → DLQ + 回滚 Redis
                channel.basicReject(deliveryTag, false);
                redisPreDeductService.rollbackQuota(Long.parseLong(message.getQuotaId()));//回滚 Redis
                log.error("名额扣减最终失败，Redis已回滚 userId={}, quotaId={}",
                        message.getUserId(), message.getQuotaId());
            }
        } catch (Exception e) {
            log.error("消费名额消息异常 userId={}", message.getUserId(), e);
            try {
                // 异常情况重新入队，让 RabbitMQ 稍后重试
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("NACK失败", ex);
            }
        }
    }

    /**
     * DLQ — 最终失败，需人工介入
     */
    @RabbitListener(queues = RabbitMQConfig.DLQ)
    public void handleDlqMessage(QuotaDeductMessage message) {
        log.error("""

                ╔══════════════════════════════════════╗
                ║  [严重告警] 名额扣减最终失败 (DLQ)    ║
                ║  userId  = {}                     ║
                ║  quotaId = {}                     ║
                ║  retries = {}                     ║
                ║  请立即人工核查并补录数据！            ║
                ╚══════════════════════════════════════╝
                """,
                message.getUserId(), message.getQuotaId(), message.getRetryCount());
    }
}
