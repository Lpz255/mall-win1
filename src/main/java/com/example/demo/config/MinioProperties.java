package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.minio")
public class MinioProperties {

    /**
     * MinIO API 地址，例如 http://127.0.0.1:9000。
     */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * 对外访问地址，留空时回退 endpoint。
     */
    private String publicEndpoint = "http://127.0.0.1:9000";

    /**
     * 访问密钥。
     */
    private String accessKey = "minioadmin";

    /**
     * 访问密钥密码。
     */
    private String secretKey = "minioadmin";

    /**
     * 存储桶名。
     */
    private String bucket = "mall-demo";

    /**
     * 商品图片目录前缀。
     */
    private String productImageDir = "product";

    /**
     * 单个图片最大大小（MB）。
     */
    private long maxImageSizeMb = 5L;

    /**
     * 启动时/上传前自动创建存储桶。
     */
    private boolean autoCreateBucket = true;

    /**
     * 自动设置公共读策略，便于前端直接访问图片。
     */
    private boolean autoSetPublicRead = true;
}
