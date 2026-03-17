package com.example.demo.service;

import com.example.demo.config.MinioProperties;
import com.example.demo.utils.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MinIO 文件存储服务。
 */
@Slf4j
@Service
public class MinioStorageService {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioProperties minioProperties;
    private final MinioClient minioClient;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public MinioStorageService(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
        this.minioClient = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }

    /**
     * 上传商品图片并返回可访问地址。
     *
     * @param file 图片文件
     * @return 包含 url、objectName 的结果
     */
    public Map<String, Object> uploadProductImage(MultipartFile file) {
        validateImage(file);
        ensureBucketReady();

        String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
        String objectName = buildObjectName(extension);
        String contentType = resolveContentType(file.getContentType(), extension);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            log.error("上传商品图片到 MinIO 失败, objectName={}", objectName, exception);
            throw new BusinessException(500, "图片上传失败，请稍后重试");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", buildPublicUrl(objectName));
        data.put("objectName", objectName);
        data.put("bucket", minioProperties.getBucket());
        return data;
    }

    private void ensureBucketReady() {
        if (bucketReady.get()) {
            return;
        }
        synchronized (this) {
            if (bucketReady.get()) {
                return;
            }
            String bucket = minioProperties.getBucket();
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    if (!minioProperties.isAutoCreateBucket()) {
                        throw new BusinessException(500, "MinIO 存储桶不存在，请先创建后再上传");
                    }
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO 存储桶不存在，已自动创建 bucket={}", bucket);
                }
                if (minioProperties.isAutoSetPublicRead()) {
                    minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(buildPublicReadPolicy(bucket))
                            .build());
                }
            } catch (BusinessException businessException) {
                throw businessException;
            } catch (Exception exception) {
                log.error("初始化 MinIO 存储桶失败, bucket={}", bucket, exception);
                throw new BusinessException(500, "MinIO 初始化失败，请检查配置后重试");
            }
            bucketReady.set(true);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请先选择要上传的图片");
        }

        long maxBytes = minioProperties.getMaxImageSizeMb() * 1024 * 1024;
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new BusinessException(400, "图片大小不能超过" + minioProperties.getMaxImageSizeMb() + "MB");
        }

        String contentType = file.getContentType();
        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        if (!isImageContentType(contentType) && !isImageExtension(extension)) {
            throw new BusinessException(400, "仅支持上传图片文件");
        }
    }

    private String buildObjectName(String extension) {
        LocalDate today = LocalDate.now();
        String datePath = today.format(DATE_PATH_FORMATTER);
        String prefix = trimSlashes(minioProperties.getProductImageDir());
        String uuid = UUID.randomUUID().toString().replace("-", "");

        if (StringUtils.hasText(prefix)) {
            return prefix + "/" + datePath + "/" + uuid + "." + extension;
        }
        return datePath + "/" + uuid + "." + extension;
    }

    private String buildPublicUrl(String objectName) {
        String endpoint = StringUtils.hasText(minioProperties.getPublicEndpoint())
                ? minioProperties.getPublicEndpoint()
                : minioProperties.getEndpoint();
        String normalizedEndpoint = trimTrailingSlash(endpoint);
        return normalizedEndpoint + "/" + minioProperties.getBucket() + "/" + objectName;
    }

    private static String resolveContentType(String contentType, String extension) {
        if (StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return contentType;
        }
        return switch (extension) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
    }

    private static String resolveExtension(String originalFileName, String contentType) {
        if (StringUtils.hasText(originalFileName)) {
            int index = originalFileName.lastIndexOf('.');
            if (index >= 0 && index < originalFileName.length() - 1) {
                String ext = originalFileName.substring(index + 1).toLowerCase(Locale.ROOT);
                if (isImageExtension(ext)) {
                    return ext;
                }
            }
        }
        if (StringUtils.hasText(contentType)) {
            String type = contentType.toLowerCase(Locale.ROOT);
            if (type.contains("png")) {
                return "png";
            }
            if (type.contains("gif")) {
                return "gif";
            }
            if (type.contains("webp")) {
                return "webp";
            }
            if (type.contains("bmp")) {
                return "bmp";
            }
        }
        return "jpg";
    }

    private static boolean isImageContentType(String contentType) {
        return StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private static boolean isImageExtension(String extension) {
        return switch (extension) {
            case "jpg", "jpeg", "png", "gif", "webp", "bmp" -> true;
            default -> false;
        };
    }

    private static String buildPublicReadPolicy(String bucket) {
        return """
                {
                  "Version":"2012-10-17",
                  "Statement":[
                    {
                      "Effect":"Allow",
                      "Principal":{"AWS":["*"]},
                      "Action":["s3:GetObject"],
                      "Resource":["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
    }

    private static String trimTrailingSlash(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String trimSlashes(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
