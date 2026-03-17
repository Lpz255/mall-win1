package com.example.demo.service;

import com.example.demo.config.RabbitMQConfig;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单延迟消息生产者。
 */
@Component
public class OrderDelayProducer {

    private final RabbitTemplate rabbitTemplate;

    public OrderDelayProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发送订单超时取消延迟消息。
     *
     * @param orderNo 订单号
     * @param delayMillis 延迟毫秒数
     */
    public void sendOrderDelayMessage(String orderNo, long delayMillis) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                orderNo,
                message -> {
                    // x-delay 由延迟交换机插件识别，单位毫秒。
                    message.getMessageProperties().setHeader("x-delay", delayMillis);
                    // 消息持久化，避免 Broker 重启导致消息丢失。
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                }
        );
    }
}