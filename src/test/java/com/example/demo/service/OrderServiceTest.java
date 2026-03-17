package com.example.demo.service;

import com.example.demo.config.OrderProperties;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单服务测试。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OrderDelayProducer orderDelayProducer;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties();
        orderProperties.setExpireMinutes(30);
        orderProperties.setDelayCancelMillis(1_800_000L);
        orderService = new OrderService(orderMapper, productMapper, orderDelayProducer, orderProperties);
    }

    /**
     * 验证订单创建。
     */
    @Test
    void shouldCreateOrderSuccess() {
        Product product = new Product();
        product.setId(1001L);
        product.setStatus(1);
        product.setStock(100);
        product.setPrice(new BigDecimal("199.00"));

        when(productMapper.selectById(1001L)).thenReturn(product);
        when(productMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        String orderNo = orderService.createOrder(1L, 1001L, "上海市浦东新区世纪大道100号");

        assertTrue(orderNo.startsWith("ORD"));
        verify(orderDelayProducer, times(1)).sendOrderDelayMessage(eq(orderNo), eq(1_800_000L));
    }

    /**
     * 验证订单查询。
     */
    @Test
    void shouldQueryOrderSuccess() {
        Order order = new Order();
        order.setOrderNo("ORD123456");
        order.setPayStatus(0);

        when(orderMapper.selectOne(any())).thenReturn(order);

        Order result = orderService.queryOrder("ORD123456");

        assertNotNull(result);
        assertEquals("ORD123456", result.getOrderNo());
    }

    /**
     * 验证手动取消订单。
     */
    @Test
    void shouldCancelOrderSuccess() {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNo("ORD888");
        order.setProductId(1001L);
        order.setPayStatus(0);

        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(productMapper.update(any(), any())).thenReturn(1);

        Order result = orderService.cancelOrder("ORD888");

        assertEquals(2, result.getPayStatus());
        verify(productMapper, times(1)).update(any(), any());
    }

    /**
     * 验证超时自动取消。
     */
    @Test
    void shouldTimeoutCancelOrderSuccess() {
        Order order = new Order();
        order.setId(20L);
        order.setOrderNo("ORD999");
        order.setProductId(1002L);
        order.setPayStatus(0);
        order.setExpireTime(LocalDateTime.now().minusMinutes(1));

        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(productMapper.update(any(), any())).thenReturn(1);

        orderService.handleTimeoutCancel("ORD999");

        verify(orderMapper, times(1)).update(any(), any());
        verify(productMapper, times(1)).update(any(), any());
    }
}