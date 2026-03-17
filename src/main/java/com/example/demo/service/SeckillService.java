package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.config.RabbitMQConfig;
import com.example.demo.config.SeckillProperties;
import com.example.demo.entity.SeckillOrderMessage;
import com.example.demo.entity.SeckillProduct;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.utils.BusinessException;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务。
 * <p>
 * 核心能力：
 * 1. 基于令牌桶限流（2000 QPS）。
 * 2. Redis 预扣库存，削峰并减少数据库写放大。
 * 3. 异步消息下单，缩短接口同步耗时。
 * </p>
 */
@Service
public class SeckillService {

    private static final int ENABLED_STATUS = 1;

    private static final DefaultRedisScript<Long> UNLOCK_LUA_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final SeckillProductMapper seckillProductMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final SeckillProperties seckillProperties;
    private final RateLimiter rateLimiter;

    public SeckillService(SeckillProductMapper seckillProductMapper,
                          StringRedisTemplate stringRedisTemplate,
                          RabbitTemplate rabbitTemplate,
                          SeckillProperties seckillProperties) {
        this.seckillProductMapper = seckillProductMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.seckillProperties = seckillProperties;
        this.rateLimiter = RateLimiter.create(seckillProperties.getPermitsPerSecond());
    }

    /**
     * 提交秒杀请求。
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @return 秒杀订单号
     */
    public String seckill(Long userId, Long productId) {
        validateRequest(userId, productId);

        // 令牌桶限流：超时 50ms 未获取到令牌则快速失败，保护系统稳定性。
        boolean acquired = rateLimiter.tryAcquire(1, seckillProperties.getAcquireTimeoutMillis(), TimeUnit.MILLISECONDS);
        if (!acquired) {
            throw new BusinessException(429, "秒杀请求过于频繁，请稍后再试");
        }

        SeckillProduct seckillProduct = queryValidSeckillProduct(productId);

        String userOrderKey = buildUserOrderKey(productId, userId);
        long userOrderKeyTtl = buildUserOrderKeyTtlSeconds(seckillProduct.getEndTime());

        // 用户防重：同一用户在活动期间只能持有一个待处理秒杀请求，防止重复下单。
        Boolean firstRequest = stringRedisTemplate.opsForValue().setIfAbsent(
                userOrderKey,
                "1",
                Duration.ofSeconds(userOrderKeyTtl)
        );
        if (!Boolean.TRUE.equals(firstRequest)) {
            throw new BusinessException(4009, "请勿重复秒杀");
        }

        String stockKey = buildStockKey(productId);
        ensureStockCache(seckillProduct, stockKey);

        // Redis 预扣库存：先在缓存层扣减，避免高并发直接打到 MySQL。
        Long remain = stringRedisTemplate.opsForValue().increment(stockKey, -1);
        if (remain == null) {
            stringRedisTemplate.delete(userOrderKey);
            throw new BusinessException(5006, "秒杀服务繁忙，请稍后重试");
        }
        if (remain < 0) {
            // 出现负库存立即回滚本次预扣，避免库存被扣穿。
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.delete(userOrderKey);
            throw new BusinessException(4010, "秒杀商品已售罄");
        }

        String orderNo = buildOrderNo(userId, productId);
        SeckillOrderMessage message = buildSeckillMessage(orderNo, userId, productId, seckillProduct);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,
                    RabbitMQConfig.SECKILL_ROUTING_KEY,
                    message,
                    msg -> {
                        // 消息持久化：Broker 重启后消息仍可恢复，降低丢单风险。
                        msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        msg.getMessageProperties().setHeader("x-retry-count", 0);
                        return msg;
                    }
            );
        } catch (AmqpException exception) {
            // 投递失败需要回滚预扣库存与用户防重标记，保证数据一致性。
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.delete(userOrderKey);
            throw new BusinessException(5007, "秒杀下单失败，请稍后重试");
        }

        return orderNo;
    }

    /**
     * 查询有效秒杀商品。
     *
     * @param productId 商品ID
     * @return 秒杀商品
     */
    private SeckillProduct queryValidSeckillProduct(Long productId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<SeckillProduct> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SeckillProduct::getProductId, productId)
                .eq(SeckillProduct::getStatus, ENABLED_STATUS)
                .le(SeckillProduct::getStartTime, now)
                .ge(SeckillProduct::getEndTime, now)
                .last("limit 1");

        SeckillProduct seckillProduct = seckillProductMapper.selectOne(queryWrapper);
        if (seckillProduct == null) {
            throw new BusinessException(4041, "当前商品未开启秒杀活动");
        }
        return seckillProduct;
    }

    /**
     * 确保秒杀库存已装载到 Redis。
     *
     * @param seckillProduct 秒杀商品
     * @param stockKey 库存缓存键
     */
    private void ensureStockCache(SeckillProduct seckillProduct, String stockKey) {
        String cachedStock = stringRedisTemplate.opsForValue().get(stockKey);
        if (StringUtils.hasText(cachedStock)) {
            return;
        }

        String initLockKey = seckillProperties.getStockInitLockKeyPrefix() + seckillProduct.getProductId();
        String lockValue = UUID.randomUUID().toString();

        if (tryLock(initLockKey, lockValue, Duration.ofSeconds(5))) {
            try {
                String secondCheck = stringRedisTemplate.opsForValue().get(stockKey);
                if (!StringUtils.hasText(secondCheck)) {
                    long ttlSeconds = buildStockCacheTtlSeconds(seckillProduct.getEndTime());
                    stringRedisTemplate.opsForValue().set(
                            stockKey,
                            String.valueOf(seckillProduct.getSeckillStock()),
                            Duration.ofSeconds(ttlSeconds)
                    );
                }
            } finally {
                unlock(initLockKey, lockValue);
            }
            return;
        }

        // 未拿到初始化锁时短暂等待，避免并发线程重复初始化库存缓存。
        for (int index = 0; index < 5; index++) {
            sleepQuietly(10);
            String waitValue = stringRedisTemplate.opsForValue().get(stockKey);
            if (StringUtils.hasText(waitValue)) {
                return;
            }
        }

        long ttlSeconds = buildStockCacheTtlSeconds(seckillProduct.getEndTime());
        stringRedisTemplate.opsForValue().setIfAbsent(
                stockKey,
                String.valueOf(seckillProduct.getSeckillStock()),
                Duration.ofSeconds(ttlSeconds)
        );
    }

    /**
     * 构建秒杀消息对象。
     */
    private SeckillOrderMessage buildSeckillMessage(String orderNo,
                                                    Long userId,
                                                    Long productId,
                                                    SeckillProduct seckillProduct) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderNo(orderNo);
        message.setUserId(userId);
        message.setProductId(productId);
        message.setSeckillPrice(seckillProduct.getSeckillPrice());
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    /**
     * 构建库存缓存键。
     */
    private String buildStockKey(Long productId) {
        return seckillProperties.getStockCacheKeyPrefix() + productId;
    }

    /**
     * 构建用户防重键。
     */
    private String buildUserOrderKey(Long productId, Long userId) {
        return seckillProperties.getUserOrderKeyPrefix() + productId + ":" + userId;
    }

    /**
     * 构建订单号。
     */
    private String buildOrderNo(Long userId, Long productId) {
        long epochMilli = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "SK" + epochMilli + userId + productId + UUID.randomUUID().toString().substring(0, 6);
    }

    /**
     * 构建库存缓存过期秒数。
     */
    private long buildStockCacheTtlSeconds(LocalDateTime endTime) {
        long raw = Duration.between(LocalDateTime.now(), endTime).toSeconds() + 3600;
        return Math.max(raw, 600);
    }

    /**
     * 构建用户防重键过期秒数。
     */
    private long buildUserOrderKeyTtlSeconds(LocalDateTime endTime) {
        long raw = Duration.between(LocalDateTime.now(), endTime).toSeconds() + 600;
        return Math.max(raw, 600);
    }

    /**
     * 尝试获取分布式锁。
     */
    private boolean tryLock(String lockKey, String lockValue, Duration duration) {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, duration);
        return Boolean.TRUE.equals(locked);
    }

    /**
     * 释放分布式锁。
     */
    private void unlock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(UNLOCK_LUA_SCRIPT, Collections.singletonList(lockKey), lockValue);
    }

    /**
     * 请求参数校验。
     */
    private void validateRequest(Long userId, Long productId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(4004, "用户ID不合法");
        }
        if (productId == null || productId <= 0) {
            throw new BusinessException(4004, "商品ID不合法");
        }
    }

    /**
     * 静默休眠。
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}