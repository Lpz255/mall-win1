package com.example.demo.utils;

import java.io.Serial;

/**
 * 业务异常。
 * <p>
 * 使用约束：
 * 1. 业务场景禁止直接抛出 RuntimeException。
 * 2. 所有可预期的业务失败应统一抛出本异常。
 * 3. code 用于前后端对齐错误语义，message 用于中文提示。
 * </p>
 */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码。
     */
    private final int code;

    /**
     * 异常提示信息（中文）。
     */
    private final String message;

    /**
     * 构造业务异常。
     *
     * @param code 业务错误码
     * @param message 异常提示
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}