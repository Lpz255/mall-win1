package com.example.demo.utils;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 加密工具类。
 * <p>
 * 提供常用摘要算法：
 * 1. MD5：适用于一致性校验场景。
 * 2. SHA-256：适用于更高安全需求的摘要场景。
 * </p>
 */
public final class EncryptUtils {

    private EncryptUtils() {
    }

    /**
     * 生成 MD5 摘要。
     *
     * @param source 原始字符串
     * @return MD5 十六进制字符串
     */
    public static String md5(String source) {
        return digest(source, "MD5");
    }

    /**
     * 生成 SHA-256 摘要。
     *
     * @param source 原始字符串
     * @return SHA-256 十六进制字符串
     */
    public static String sha256(String source) {
        return digest(source, "SHA-256");
    }

    /**
     * 通用摘要计算。
     *
     * @param source 原始字符串
     * @param algorithm 算法名称
     * @return 十六进制摘要
     */
    private static String digest(String source, String algorithm) {
        if (!StringUtils.hasText(source)) {
            throw new BusinessException(4001, "待加密内容不能为空");
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            byte[] bytes = messageDigest.digest(source.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(5001, "不支持的加密算法");
        }
    }

    /**
     * 字节数组转十六进制字符串。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}