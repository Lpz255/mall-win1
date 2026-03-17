package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户登录会话配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth.user-session")
public class UserSessionProperties {

    /**
     * 用户 token 存储键前缀：auth:user:token:{token} -> userId
     */
    private String tokenKeyPrefix = "auth:user:token:";

    /**
     * 用户索引键前缀：auth:user:index:{userId} -> token
     */
    private String userIndexKeyPrefix = "auth:user:index:";

    /**
     * 登录态过期时长（秒）。
     */
    private long ttlSeconds = 604800L;
}
