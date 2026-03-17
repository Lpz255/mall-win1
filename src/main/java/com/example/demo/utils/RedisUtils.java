package com.example.demo.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存工具类。
 * <p>
 * 设计说明：
 * 1. 提供基础的 get/set/delete 封装，降低重复代码。
 * 2. 建议业务侧对热点数据设置合理过期时间，避免缓存雪崩。
 * 3. 建议空值缓存短 TTL，降低缓存穿透风险。
 * </p>
 */
@Component
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 构造方法注入 RedisTemplate。
     *
     * @param redisTemplate RedisTemplate
     */
    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取缓存。
     *
     * @param key 缓存键
     * @return 缓存值
     */
    public Object get(String key) {
        validateKey(key);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取缓存并转换为指定类型。
     *
     * @param key 缓存键
     * @param clazz 目标类型
     * @param <T> 泛型类型
     * @return 转换后的对象，未命中返回 null
     */
    public <T> T get(String key, Class<T> clazz) {
        validateKey(key);
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        if (!clazz.isInstance(value)) {
            throw new BusinessException(5002, "缓存数据类型不匹配");
        }
        return clazz.cast(value);
    }

    /**
     * 写入缓存（不过期）。
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    public void set(String key, Object value) {
        validateKey(key);
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 写入缓存（带过期时间）。
     *
     * @param key 缓存键
     * @param value 缓存值
     * @param timeout 过期时长
     * @param unit 时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        validateKey(key);
        if (timeout <= 0) {
            throw new BusinessException(4002, "缓存过期时间必须大于0");
        }
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 删除缓存。
     *
     * @param key 缓存键
     * @return true 表示删除成功，false 表示未删除
     */
    public boolean delete(String key) {
        validateKey(key);
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 校验缓存键。
     *
     * @param key 缓存键
     */
    private void validateKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(4003, "缓存键不能为空");
        }
    }
}