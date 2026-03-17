package com.example.demo.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 统一返回结果测试。
 */
class ResultTest {

    /**
     * 验证 success() 返回值。
     */
    @Test
    void shouldBuildSuccessWithoutData() {
        Result result = Result.success();
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertNull(result.getData());
    }

    /**
     * 验证 success(T data) 返回值。
     */
    @Test
    void shouldBuildSuccessWithData() {
        Result result = Result.success("测试数据");
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertEquals("测试数据", result.getData());
    }

    /**
     * 验证 fail(int, String) 返回值。
     */
    @Test
    void shouldBuildFailWithCodeAndMessage() {
        Result result = Result.fail(4001, "参数错误");
        assertEquals(4001, result.getCode());
        assertEquals("参数错误", result.getMessage());
        assertNull(result.getData());
    }

    /**
     * 验证 fail(String) 返回值。
     */
    @Test
    void shouldBuildFailWithDefaultCode() {
        Result result = Result.fail("系统繁忙，请稍后再试");
        assertEquals(500, result.getCode());
        assertEquals("系统繁忙，请稍后再试", result.getMessage());
        assertNull(result.getData());
    }
}