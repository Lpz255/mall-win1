package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.entity.Product;
import com.example.demo.entity.SeckillOrder;
import com.example.demo.entity.SeckillProduct;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.SeckillOrderMapper;
import com.example.demo.mapper.SeckillProductMapper;
import com.example.demo.support.DemoContextService;
import com.example.demo.utils.BusinessException;
import com.example.demo.utils.Result;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 秒杀接口。
 */
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private final SeckillProductMapper seckillProductMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final ProductMapper productMapper;
    private final DemoContextService demoContextService;

    public SeckillController(SeckillProductMapper seckillProductMapper,
                             SeckillOrderMapper seckillOrderMapper,
                             ProductMapper productMapper,
                             DemoContextService demoContextService) {
        this.seckillProductMapper = seckillProductMapper;
        this.seckillOrderMapper = seckillOrderMapper;
        this.productMapper = productMapper;
        this.demoContextService = demoContextService;
    }

    @GetMapping("/list")
    public Result list(@RequestParam(value = "size", required = false) Integer size) {
        int limit = size == null || size <= 0 ? 20 : Math.min(size, 100);
        List<SeckillProduct> activities = seckillProductMapper.selectList(new LambdaQueryWrapper<SeckillProduct>()
                .orderByDesc(SeckillProduct::getStartTime));

        Map<Long, Product> productMap = loadProductMap(activities.stream().map(SeckillProduct::getProductId).toList());
        Map<Long, Long> salesMap = buildSalesMap();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SeckillProduct activity : activities) {
            Product product = productMap.get(activity.getProductId());
            rows.add(buildSeckillRow(activity, product, salesMap.getOrDefault(activity.getProductId(), 0L)));
        }
        rows.sort(Comparator.comparing(item -> String.valueOf(item.get("startTime")), Comparator.reverseOrder()));
        return Result.success(rows.stream().limit(limit).toList());
    }

    @Transactional
    @PostMapping("/{productId}")
    public Result doSeckill(@RequestHeader(value = "Authorization", required = false) String authorization,
                            @PathVariable("productId") Long productId,
                            @RequestParam(value = "userId", required = false) Long userIdParam) {
        Long userId = resolveUserId(authorization, userIdParam);
        if (productId == null || productId <= 0) {
            throw new BusinessException(400, "商品ID不合法");
        }

        LocalDateTime now = LocalDateTime.now();
        SeckillProduct activity = seckillProductMapper.selectOne(new LambdaQueryWrapper<SeckillProduct>()
                .eq(SeckillProduct::getProductId, productId)
                .last("limit 1"));
        if (activity == null) {
            throw new BusinessException(404, "当前商品未配置秒杀活动");
        }
        if (!Objects.equals(activity.getStatus(), 1)) {
            throw new BusinessException(400, "秒杀活动未开启");
        }
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            throw new BusinessException(400, "秒杀活动尚未开始");
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            throw new BusinessException(400, "秒杀活动已结束");
        }

        SeckillOrder exists = seckillOrderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getUserId, userId)
                .eq(SeckillOrder::getProductId, productId)
                .last("limit 1"));
        if (exists != null) {
            throw new BusinessException(409, "请勿重复秒杀");
        }

        int updateRows = seckillProductMapper.update(null, new LambdaUpdateWrapper<SeckillProduct>()
                .eq(SeckillProduct::getId, activity.getId())
                .ge(SeckillProduct::getSeckillStock, 1)
                .setSql("seckill_stock = seckill_stock - 1"));
        if (updateRows <= 0) {
            throw new BusinessException(4008, "秒杀库存不足");
        }

        String orderNo = "SK" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setSeckillPrice(activity.getSeckillPrice() == null ? BigDecimal.ZERO : activity.getSeckillPrice());
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());
        int insertRows = seckillOrderMapper.insert(order);
        if (insertRows <= 0) {
            throw new BusinessException(500, "秒杀下单失败");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", orderNo);
        result.put("productId", productId);
        return Result.success(result);
    }

    private Map<Long, Product> loadProductMap(List<Long> productIds) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>().in(Product::getId, ids));
        return products.stream().collect(Collectors.toMap(Product::getId, item -> item, (a, b) -> a));
    }

    private Map<Long, Long> buildSalesMap() {
        List<SeckillOrder> orders = seckillOrderMapper.selectList(new LambdaQueryWrapper<SeckillOrder>()
                .select(SeckillOrder::getProductId));
        Map<Long, Long> sales = new LinkedHashMap<>();
        for (SeckillOrder order : orders) {
            if (order.getProductId() == null) {
                continue;
            }
            sales.put(order.getProductId(), sales.getOrDefault(order.getProductId(), 0L) + 1);
        }
        return sales;
    }

    private static Map<String, Object> buildSeckillRow(SeckillProduct activity, Product product, Long sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", activity.getId());
        row.put("productId", activity.getProductId());
        row.put("name", product == null ? "秒杀商品" : product.getName());
        row.put("productName", product == null ? "秒杀商品" : product.getName());
        row.put("seckillPrice", safePrice(activity.getSeckillPrice()));
        row.put("price", safePrice(activity.getSeckillPrice()));
        row.put("originalPrice", product == null ? safePrice(activity.getSeckillPrice()).multiply(new BigDecimal("1.2"))
                : safePrice(product.getPrice()));
        row.put("stock", activity.getSeckillStock() == null ? 0 : activity.getSeckillStock());
        row.put("sales", sales == null ? 0 : sales);
        row.put("image", product == null ? "https://via.placeholder.com/640x360?text=Seckill" : normalizeImage(product.getImageUrl()));
        row.put("cover", product == null ? "https://via.placeholder.com/640x360?text=Seckill" : normalizeImage(product.getImageUrl()));
        row.put("startTime", DemoContextService.formatDateTime(activity.getStartTime()));
        row.put("endTime", DemoContextService.formatDateTime(activity.getEndTime()));
        row.put("status", buildStatus(activity));
        return row;
    }

    private static String normalizeImage(String imageUrl) {
        if (StringUtils.hasText(imageUrl)) {
            return imageUrl;
        }
        return "https://via.placeholder.com/640x360?text=Seckill";
    }

    private static BigDecimal safePrice(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String buildStatus(SeckillProduct activity) {
        LocalDateTime now = LocalDateTime.now();
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            return "ended";
        }
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            return "draft";
        }
        if (Objects.equals(activity.getStatus(), 1)) {
            return "running";
        }
        return "stopped";
    }

    private Long resolveUserId(String authorization, Long userIdParam) {
        if (StringUtils.hasText(authorization)) {
            return demoContextService.requireUserId(authorization);
        }
        if (userIdParam != null) {
            return userIdParam;
        }
        throw new BusinessException(401, "请先登录");
    }
}

