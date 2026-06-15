package com.myy.medihealth.common.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置 — 秒杀名额持久化队列    http://localhost:15672/
 *
 * 架构：
 *   quota.deduct.exchange (主交换机)
 *        └─ quota.deduct.queue (主队列, TTL 30min)
 *              └─ 消息30分钟未消费 → 死信
 *                    ↓
 *   quota.deduct.dlx (死信交换机)
 *        └─ quota.deduct.dlq (死信队列)
 *              └─ DLQ 消费者 → 回滚 Redis + 告警
 */
@Configuration
public class RabbitMQConfig {

    // ========== 主队列配置 ==========
    public static final String MAIN_EXCHANGE = "quota.deduct.exchange";
    public static final String MAIN_QUEUE = "quota.deduct.queue";
    public static final String MAIN_ROUTING_KEY = "quota.deduct";

    // ========== 死信配置 ==========
    public static final String DLX_EXCHANGE = "quota.deduct.dlx";
    public static final String DLQ_QUEUE = "quota.deduct.dlq";
    public static final String DLX_ROUTING_KEY = "quota.deduct.dlq";

    // ========== 1. 主交换机 ==========
    @Bean
    public DirectExchange quotaDeductMainExchange() {
        return new DirectExchange(MAIN_EXCHANGE);
    }

    // ========== 2. 死信交换机 ==========
    @Bean
    public DirectExchange quotaDeductDlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // ========== 3. 主队列（配置死信） ==========
    @Bean
    public Queue quotaDeductMainQueue() {
        Map<String, Object> args = new HashMap<>();
        // 死信交换机
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        // 死信路由键
        args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);
        // 消息 TTL 30分钟
        args.put("x-message-ttl", 30 * 60 * 1000);

        return QueueBuilder.durable(MAIN_QUEUE)
                .withArguments(args)
                .build();
    }

    // ========== 4. 死信队列 ==========
    @Bean
    public Queue quotaDeductDlq() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    // ========== 5. 主队列绑定 ==========
    @Bean
    public Binding quotaDeductMainBinding() {
        return BindingBuilder.bind(quotaDeductMainQueue())
                .to(quotaDeductMainExchange())
                .with(MAIN_ROUTING_KEY);
    }

    // ========== 6. 死信队列绑定 ==========
    @Bean
    public Binding quotaDeductDlqBinding() {
        return BindingBuilder.bind(quotaDeductDlq())
                .to(quotaDeductDlxExchange())
                .with(DLX_ROUTING_KEY);
    }
}