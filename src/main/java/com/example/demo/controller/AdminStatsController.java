package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.SeckillOrder;
import com.example.demo.entity.SeckillProduct;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.support.DemoContextService;
import com.example.demo.utils.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 后台统计接口。
 */
@RestController
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private final DemoContextService demoContextService;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final SeckillProductMapper seckillProductMapper;
    private final SeckillOrderMapper seckillOrderMapper;

    public AdminStatsController(DemoContextService demoContextService,
                                OrderMapper orderMapper,
                                ProductMapper productMapper,
                                SeckillProductMapper seckillProductMapper,
                                SeckillOrderMapper seckillOrderMapper) {
        this.demoContextService = demoContextService;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.seckillProductMapper = seckillProductMapper;
        this.seckillOrderMapper = seckillOrderMapper;
    }

    @GetMapping("/overview")
    public Result overview(@RequestHeader(value = "Authorization", required = false) String authorization) {
        demoContextService.getAdminPermissions(authorization);

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>());
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>());
        List<SeckillProduct> seckillProducts = seckillProductMapper.selectList(new LambdaQueryWrapper<SeckillProduct>());
        List<SeckillOrder> seckillOrders = seckillOrderMapper.selectList(new LambdaQueryWrapper<SeckillOrder>());

        LocalDate today = LocalDate.now();
        long orderCount = orders.size();
        long todayOrderCount = orders.stream().filter(item -> isToday(item.getCreateTime(), today)).count();
        BigDecimal salesAmount = orders.stream()
                .filter(item -> !Objects.equals(item.getPayStatus(), 2))
                .map(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal todaySalesAmount = orders.stream()
                .filter(item -> isToday(item.getCreateTime(), today) && !Objects.equals(item.getPayStatus(), 2))
                .map(item -> item.getAmount() == null ? BigDecimal.ZERO : item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        long waitPayCount = orders.stream().filter(item -> Objects.equals(item.getPayStatus(), 0)).count();
        long waitShipCount = orders.stream().filter(item -> Objects.equals(item.getPayStatus(), 1)).count();
        long waitRefundCount = 0L;
        long onSaleProductCount = products.stream().filter(item -> Objects.equals(item.getStatus(), 1)).count();
        long activeSeckillCount = seckillProducts.stream().filter(item -> Objects.equals(item.getStatus(), 1)).count();
        long userCount = demoContextService.countUsers("", "");

        double seckillRate = orderCount == 0 ? 0D : ((double) seckillOrders.size() / (double) orderCount) * 100D;
        double seckillRateDiff = seckillRate * 0.08D;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCount", orderCount);
        data.put("todayOrderCount", todayOrderCount);
        data.put("salesAmount", salesAmount);
        data.put("todaySalesAmount", todaySalesAmount);
        data.put("seckillRate", roundRate(seckillRate));
        data.put("seckillRateDiff", roundRate(seckillRateDiff));
        data.put("waitPayCount", waitPayCount);
        data.put("waitShipCount", waitShipCount);
        data.put("waitRefundCount", waitRefundCount);
        data.put("onSaleProductCount", onSaleProductCount);
        data.put("activeSeckillCount", activeSeckillCount);
        data.put("userCount", userCount);
        return Result.success(data);
    }

    private static boolean isToday(LocalDateTime value, LocalDate today) {
        return value != null && Objects.equals(value.toLocalDate(), today);
    }

    private static double roundRate(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
