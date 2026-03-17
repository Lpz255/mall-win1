package com.example.demo.utils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一接口返回结果。
 * <p>
 * 规范说明：
 * 1. code：业务状态码，200 表示成功，其它值表示失败。
 * 2. message：中文提示信息，面向调用方可读。
 * 3. data：业务返回数据，可为空。
 * </p>
 */
public class Result implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 成功状态码。 */
    private static final int SUCCESS_CODE = 200;
    /** 默认成功提示。 */
    private static final String SUCCESS_MESSAGE = "操作成功";
    /** 默认失败状态码。 */
    private static final int DEFAULT_FAIL_CODE = 500;

    /**
     * 业务状态码。
     */
    private int code;

    /**
     * 提示信息（中文）。
     */
    private String message;

    /**
     * 返回数据。
     */
    private Object data;

    /**
     * 无参构造方法。
     */
    public Result() {
    }

    /**
     * 全参构造方法。
     *
     * @param code 状态码
     * @param message 提示信息
     * @param data 返回数据
     */
    public Result(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 返回成功结果（无数据）。
     *
     * @return Result
     */
    public static Result success() {
        return new Result(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /**
     * 返回成功结果（含数据）。
     *
     * @param data 返回数据
     * @param <T> 数据类型
     * @return Result
     */
    public static <T> Result success(T data) {
        return new Result(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 返回失败结果。
     *
     * @param code 业务状态码
     * @param message 失败提示
     * @return Result
     */
    public static Result fail(int code, String message) {
        return new Result(code, message, null);
    }

    /**
     * 返回失败结果（使用默认失败码）。
     *
     * @param message 失败提示
     * @return Result
     */
    public static Result fail(String message) {
        return new Result(DEFAULT_FAIL_CODE, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}