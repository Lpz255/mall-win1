package com.example.demo.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基础中间件连接验证测试。
 * <p>
 * 使用说明：
 * 1. 本地先启动 MySQL、Redis、RabbitMQ。
 * 2. 去掉 @Disabled 后执行测试。
 * 3. 该类用于验证基础配置是否连通，不承载业务测试逻辑。
 * </p>
 */
@SpringBootTest
@Disabled("需先启动本地 MySQL/Redis/RabbitMQ 后执行")
class InfrastructureConnectionTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 验证 Redis 连通性。
     */
    @Test
    void testRedisConnection() {
        String pong = stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
        assertEquals("PONG", pong, "Redis 连接失败或未返回 PONG");
    }

    /**
     * 验证 MySQL 连通性。
     */
    @Test
    void testMysqlConnection() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertEquals(1, result, "MySQL 连接失败，SELECT 1 未返回 1");
    }

    /**
     * 验证 RabbitMQ 连接通道状态。
     */
    @Test
    void testRabbitMqConnection() {
        Boolean opened = rabbitTemplate.execute(channel -> channel.isOpen());
        assertTrue(Boolean.TRUE.equals(opened), "RabbitMQ 通道未打开");
    }
}