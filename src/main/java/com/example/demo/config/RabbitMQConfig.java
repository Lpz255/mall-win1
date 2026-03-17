package com.example.demo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类。
 * <p>
 * 说明：
 * 1. 提供通用延迟交换机与绑定关系。
 * 2. 提供秒杀交换机与队列绑定。
 * 3. 提供订单超时取消延迟队列。
 * 4. 提供 JSON 消息转换器，降低消息体编码问题风险。
 * </p>
 */
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    /** 延迟交换机名称。 */
    public static final String DELAY_EXCHANGE = "demo.delay.exchange";
    /** 延迟队列名称。 */
    public static final String DELAY_QUEUE = "demo.delay.queue";
    /** 延迟路由键。 */
    public static final String DELAY_ROUTING_KEY = "demo.delay.routing.key";

    /** 秒杀交换机名称。 */
    public static final String SECKILL_EXCHANGE = "demo.seckill.exchange";
    /** 秒杀队列名称。 */
    public static final String SECKILL_QUEUE = "demo.seckill.queue";
    /** 秒杀路由键。 */
    public static final String SECKILL_ROUTING_KEY = "demo.seckill.routing.key";

    /** 订单延迟交换机名称。 */
    public static final String ORDER_DELAY_EXCHANGE = "demo.order.delay.exchange";
    /** 订单延迟队列名称。 */
    public static final String ORDER_DELAY_QUEUE = "demo.order.delay.queue";
    /** 订单延迟路由键。 */
    public static final String ORDER_DELAY_ROUTING_KEY = "demo.order.delay.routing.key";

    /**
     * 声明通用延迟交换机。
     *
     * @return 延迟交换机实例
     */
    @Bean
    public CustomExchange delayExchange() {
        Map<String, Object> arguments = new HashMap<>(1);
        arguments.put("x-delayed-type", "direct");
        return new CustomExchange(DELAY_EXCHANGE, "x-delayed-message", true, false, arguments);
    }

    /**
     * 声明通用延迟队列。
     *
     * @return 延迟队列实例
     */
    @Bean
    public Queue delayQueue() {
        return new Queue(DELAY_QUEUE, true, false, false);
    }

    /**
     * 绑定通用延迟队列与交换机。
     *
     * @param delayQueue 延迟队列
     * @param delayExchange 延迟交换机
     * @return 绑定关系
     */
    @Bean
    public Binding delayBinding(@Qualifier("delayQueue") Queue delayQueue,
                                @Qualifier("delayExchange") CustomExchange delayExchange) {
        return BindingBuilder.bind(delayQueue).to(delayExchange).with(DELAY_ROUTING_KEY).noargs();
    }

    /**
     * 秒杀业务交换机（Direct）。
     *
     * @return DirectExchange
     */
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    /**
     * 秒杀业务队列（持久化）。
     *
     * @return Queue
     */
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE).build();
    }

    /**
     * 绑定秒杀队列与秒杀交换机。
     *
     * @param seckillQueue 秒杀队列
     * @param seckillExchange 秒杀交换机
     * @return 绑定关系
     */
    @Bean
    public Binding seckillBinding(@Qualifier("seckillQueue") Queue seckillQueue,
                                  @Qualifier("seckillExchange") DirectExchange seckillExchange) {
        return BindingBuilder.bind(seckillQueue).to(seckillExchange).with(SECKILL_ROUTING_KEY);
    }

    /**
     * 订单超时取消延迟交换机。
     *
     * @return CustomExchange
     */
    @Bean
    public CustomExchange orderDelayExchange() {
        Map<String, Object> arguments = new HashMap<>(1);
        arguments.put("x-delayed-type", "direct");
        return new CustomExchange(ORDER_DELAY_EXCHANGE, "x-delayed-message", true, false, arguments);
    }

    /**
     * 订单超时取消延迟队列（持久化）。
     *
     * @return Queue
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE).build();
    }

    /**
     * 绑定订单延迟队列与订单延迟交换机。
     *
     * @param orderDelayQueue 订单延迟队列
     * @param orderDelayExchange 订单延迟交换机
     * @return 绑定关系
     */
    @Bean
    public Binding orderDelayBinding(@Qualifier("orderDelayQueue") Queue orderDelayQueue,
                                     @Qualifier("orderDelayExchange") CustomExchange orderDelayExchange) {
        return BindingBuilder.bind(orderDelayQueue).to(orderDelayExchange).with(ORDER_DELAY_ROUTING_KEY).noargs();
    }

    /**
     * 配置 RabbitMQ 消息 JSON 转换器。
     *
     * @return Jackson2JsonMessageConverter
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}