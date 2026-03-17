package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.config.RabbitMQConfig;
import com.example.demo.config.SeckillProperties;
import com.example.demo.entity.SeckillOrder;
import com.example.demo.entity.SeckillOrderMessage;
import com.example.demo.entity.SeckillProduct;
import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.utils.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 秒杀消息消费端。
 * <p>
 * 处理流程：
 * 1. 消费秒杀下单消息。
 * 2. 扣减 MySQL 秒杀库存并创建订单。
 * 3. 失败重试，超过阈值后执行库存补偿。
 * </p>
 */
@Component
public class SeckillConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeckillConsumer.class);
    private static final int ENABLED_STATUS = 1;

    private final ObjectMapper objectMapper;
    private final SeckillProductMapper seckillProductMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final SeckillProperties seckillProperties;

    public SeckillConsumer(ObjectMapper objectMapper,
                           SeckillProductMapper seckillProductMapper,
                           SeckillOrderMapper seckillOrderMapper,
                           StringRedisTemplate stringRedisTemplate,
                           RabbitTemplate rabbitTemplate,
                           SeckillProperties seckillProperties) {
        this.objectMapper = objectMapper;
        this.seckillProductMapper = seckillProductMapper;
        this.seckillOrderMapper = seckillOrderMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.seckillProperties = seckillProperties;
    }

    /**
     * 消费秒杀下单消息。
     *
     * @param message 原始消息
     * @param channel RabbitMQ 通道
     * @param deliveryTag 投递标签
     * @throws IOException IO异常
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void consumeSeckillMessage(Message message,
                                      Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        SeckillOrderMessage orderMessage = null;
        try {
            orderMessage = objectMapper.readValue(message.getBody(), SeckillOrderMessage.class);
            processSeckillOrder(orderMessage);
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            int currentRetryCount = extractRetryCount(message);
            int maxRetryTimes = Math.max(seckillProperties.getMessageMaxRetryTimes(), 1);

            if (currentRetryCount + 1 < maxRetryTimes) {
                republishRetryMessage(message, currentRetryCount + 1);
                channel.basicAck(deliveryTag, false);
                LOGGER.warn("秒杀消息消费失败，准备第{}次重试，message={}", currentRetryCount + 1, safeMessageBody(message));
                return;
            }

            // 达到最大重试次数后执行库存与防重标记补偿，避免用户被永久阻塞。
            if (orderMessage != null) {
                compensateStockAndUserMarker(orderMessage);
            }
            channel.basicAck(deliveryTag, false);
            LOGGER.error("秒杀消息消费最终失败，已执行补偿，message={}", safeMessageBody(message), exception);
        }
    }

    /**
     * 处理秒杀订单消息。
     *
     * @param orderMessage 秒杀消息
     */
    @Transactional(rollbackFor = Exception.class)
    public void processSeckillOrder(SeckillOrderMessage orderMessage) {
        validateMessage(orderMessage);

        SeckillOrder existingOrder = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getOrderNo, orderMessage.getOrderNo())
                        .last("limit 1")
        );
        if (existingOrder != null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int updateRows = seckillProductMapper.update(
                null,
                new LambdaUpdateWrapper<SeckillProduct>()
                        .eq(SeckillProduct::getProductId, orderMessage.getProductId())
                        .eq(SeckillProduct::getStatus, ENABLED_STATUS)
                        .le(SeckillProduct::getStartTime, now)
                        .ge(SeckillProduct::getEndTime, now)
                        .gt(SeckillProduct::getSeckillStock, 0)
                        .setSql("seckill_stock = seckill_stock - 1")
        );

        if (updateRows <= 0) {
            throw new BusinessException(4010, "秒杀库存不足");
        }

        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setOrderNo(orderMessage.getOrderNo());
        seckillOrder.setUserId(orderMessage.getUserId());
        seckillOrder.setProductId(orderMessage.getProductId());
        seckillOrder.setSeckillPrice(Objects.requireNonNullElse(orderMessage.getSeckillPrice(), BigDecimal.ZERO));
        seckillOrder.setStatus(1);
        seckillOrder.setCreateTime(LocalDateTime.now());

        int insertRows = seckillOrderMapper.insert(seckillOrder);
        if (insertRows <= 0) {
            throw new BusinessException(5008, "秒杀订单创建失败");
        }
    }

    /**
     * 重新投递重试消息。
     *
     * @param originMessage 原消息
     * @param retryCount 重试次数
     */
    private void republishRetryMessage(Message originMessage, int retryCount) {
        Message retryMessage = MessageBuilder.withBody(originMessage.getBody())
                .setContentType(originMessage.getMessageProperties().getContentType())
                .setContentEncoding("UTF-8")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("x-retry-count", retryCount)
                .build();
        rabbitTemplate.send(RabbitMQConfig.SECKILL_EXCHANGE, RabbitMQConfig.SECKILL_ROUTING_KEY, retryMessage);
    }

    /**
     * 达到重试上限后的补偿逻辑。
     *
     * @param orderMessage 秒杀消息
     */
    private void compensateStockAndUserMarker(SeckillOrderMessage orderMessage) {
        String stockKey = seckillProperties.getStockCacheKeyPrefix() + orderMessage.getProductId();
        String userOrderKey = seckillProperties.getUserOrderKeyPrefix()
                + orderMessage.getProductId() + ":" + orderMessage.getUserId();

        stringRedisTemplate.opsForValue().increment(stockKey);
        stringRedisTemplate.delete(userOrderKey);
    }

    /**
     * 从消息头提取重试次数。
     *
     * @param message 消息
     * @return 重试次数
     */
    private int extractRetryCount(Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        Object retryValue = headers.get("x-retry-count");
        if (retryValue instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * 消息体安全输出。
     *
     * @param message 原消息
     * @return 可读消息内容
     */
    private String safeMessageBody(Message message) {
        try {
            return new String(message.getBody(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return "<unreadable-message>";
        }
    }

    /**
     * 消息基础校验。
     *
     * @param orderMessage 秒杀消息
     */
    private void validateMessage(SeckillOrderMessage orderMessage) {
        if (orderMessage == null
                || orderMessage.getUserId() == null
                || orderMessage.getUserId() <= 0
                || orderMessage.getProductId() == null
                || orderMessage.getProductId() <= 0
                || !org.springframework.util.StringUtils.hasText(orderMessage.getOrderNo())) {
            throw new BusinessException(4004, "秒杀消息参数不合法");
        }
    }
}