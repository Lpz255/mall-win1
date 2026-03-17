package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 订单模块配置参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    /**
     * 订单过期分钟数。
     */
    private long expireMinutes = 30L;

    /**
     * 订单延迟取消毫秒数。
     */
    private long delayCancelMillis = 1_800_000L;
}