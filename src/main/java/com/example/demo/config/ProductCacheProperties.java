package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品缓存参数配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "product.cache")
public class ProductCacheProperties {

    /**
     * 普通商品缓存过期秒数。
     */
    private long detailExpireSeconds = 3600;

    /**
     * 热点商品缓存过期秒数。
     */
    private long hotExpireSeconds = 1800;

    /**
     * 空对象缓存过期秒数。
     */
    private long nullExpireSeconds = 120;

    /**
     * 过期随机偏移上限秒数。
     */
    private long ttlRandomMaxSeconds = 300;

    /**
     * 互斥锁过期秒数。
     */
    private long lockExpireSeconds = 10;

    /**
     * 热点锁重试次数。
     */
    private int lockRetryTimes = 5;

    /**
     * 热点锁重试间隔毫秒。
     */
    private long lockRetryIntervalMillis = 20;

    /**
     * 热点商品ID集合。
     */
    private List<Long> hotProductIds = new ArrayList<>();
}