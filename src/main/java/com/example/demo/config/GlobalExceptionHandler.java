package com.example.demo.config;

import com.example.demo.utils.BusinessException;
import com.example.demo.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * 处理策略：
 * 1. 业务异常：记录 WARN 日志并返回可读中文提示。
 * 2. 系统异常：记录 ERROR 日志并返回统一兜底文案。
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 统一响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException exception) {
        LOGGER.warn("业务异常，code={}, message={}", exception.getCode(), exception.getMessage());
        return Result.fail(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理系统异常。
     *
     * @param exception 系统异常
     * @return 统一响应
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception exception) {
        LOGGER.error("系统异常，请关注堆栈信息", exception);
        return Result.fail(500, "系统繁忙，请稍后再试");
    }
}