package com.myy.medihealth.flashSale.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置 — 秒杀名额持久化队列
 *
 * 架构：
 *   quota.deduct.exchange (直连)
 *     ├─ quota.deduct.queue (主队列, TTL 30min)
 *     │     └─ 消费者重试 DB 乐观锁落库
 *     └─ quota.deduct.dlq (死信队列)
 *           └─ DLQ 消费者 → 回滚 Redis + 告警
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "quota.deduct.exchange";
    public static final String QUEUE = "quota.deduct.queue";
    public static final String DLQ = "quota.deduct.dlq";
    public static final String ROUTING_KEY = "quota.deduct";

    /** 死信交换机 */
    @Bean
    public DirectExchange quotaDeductExchange() {
        return new DirectExchange(EXCHANGE);
    }

    /** 主队列：绑定死信交换机，消息 TTL 30 分钟后转入 DLQ */
    @Bean
    public Queue quotaDeductQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(DLQ)
                .ttl(30 * 60 * 1000) // 30 分钟未消费则转入 DLQ
                .build();
    }

    /** 死信队列 */
    @Bean
    public Queue quotaDeductDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    /** 主队列绑定 */
    @Bean
    public Binding quotaDeductBinding() {
        return BindingBuilder.bind(quotaDeductQueue())
                .to(quotaDeductExchange())
                .with(ROUTING_KEY);
    }

    /** 死信队列绑定 */
    @Bean
    public Binding quotaDeductDlqBinding() {
        return BindingBuilder.bind(quotaDeductDlq())
                .to(quotaDeductExchange())
                .with(DLQ);
    }
}
