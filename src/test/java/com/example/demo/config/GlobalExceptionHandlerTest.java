package com.example.demo.config;

import com.example.demo.utils.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器测试。
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionThrowController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 验证 BusinessException 处理逻辑。
     */
    @Test
    void shouldHandleBusinessException() throws Exception {
        mockMvc.perform(get("/test-exception/business").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4008))
                .andExpect(jsonPath("$.message").value("商品库存不足"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    /**
     * 验证系统异常处理逻辑。
     */
    @Test
    void shouldHandleSystemException() throws Exception {
        mockMvc.perform(get("/test-exception/system").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后再试"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    /**
     * 测试专用控制器，用于主动抛出异常。
     */
    @RestController
    @RequestMapping("/test-exception")
    static class ExceptionThrowController {

        @GetMapping("/business")
        public String businessException() {
            throw new BusinessException(4008, "商品库存不足");
        }

        @GetMapping("/system")
        public String systemException() {
            throw new IllegalStateException("模拟系统异常");
        }
    }
}