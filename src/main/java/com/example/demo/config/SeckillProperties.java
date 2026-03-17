package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 秒杀模块配置参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "seckill")
public class SeckillProperties {

    /** 每秒生成令牌数量。 */
    private double permitsPerSecond = 2000D;

    /** 获取令牌超时时间（毫秒）。 */
    private long acquireTimeoutMillis = 50L;

    /** 秒杀库存缓存键前缀。 */
    private String stockCacheKeyPrefix = "seckill:stock:";

    /** 秒杀库存初始化锁键前缀。 */
    private String stockInitLockKeyPrefix = "seckill:stock:init:lock:";

    /** 用户秒杀下单防重键前缀。 */
    private String userOrderKeyPrefix = "seckill:user:order:";

    /** 消息最大重试次数。 */
    private int messageMaxRetryTimes = 3;
}