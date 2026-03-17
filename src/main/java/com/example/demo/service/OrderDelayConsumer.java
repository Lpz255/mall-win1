package com.example.demo.service;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 订单延迟消息消费者。
 */
@Component
public class OrderDelayConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderDelayConsumer.class);

    private final OrderService orderService;

    public OrderDelayConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 消费订单延迟消息。
     *
     * @param message 原始消息
     * @param channel RabbitMQ 通道
     * @param deliveryTag 投递标签
     * @throws IOException IO异常
     */
    @RabbitListener(queues = com.example.demo.config.RabbitMQConfig.ORDER_DELAY_QUEUE)
    public void consumeOrderDelayMessage(Message message,
                                         Channel channel,
                                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            String orderNo = new String(message.getBody(), StandardCharsets.UTF_8);
            if (!StringUtils.hasText(orderNo)) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 到达延迟时间后再次校验订单状态，未支付则自动取消。
            orderService.handleTimeoutCancel(orderNo);
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            LOGGER.error("订单延迟取消消费失败，消息将重入队列", exception);
            channel.basicNack(deliveryTag, false, true);
        }
    }
}