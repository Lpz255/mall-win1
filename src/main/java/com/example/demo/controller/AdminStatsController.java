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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 后台统计接口。
 * <p>
 * 聚合订单、商品、秒杀和用户等多维度数据，
 * 用于后台首页概览卡片和经营数据展示。
 * </p>
 */
@RestController
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private static final DateTimeFormatter TREND_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final int LOW_STOCK_THRESHOLD = 5;

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
        StatsSnapshot snapshot = loadStatsSnapshot();
        return Result.success(buildOverview(snapshot));
    }

    @GetMapping("/monitor")
    public Result monitor(@RequestHeader(value = "Authorization", required = false) String authorization) {
        demoContextService.getAdminPermissions(authorization);
        StatsSnapshot snapshot = loadStatsSnapshot();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overview", buildOverview(snapshot));
        data.put("trend7d", buildTrend7d(snapshot.orders()));
        data.put("orderStatusDistribution", buildOrderStatusDistribution(snapshot.orders()));
        data.put("topProducts", buildTopProducts(snapshot.orders(), snapshot.products()));
        data.put("seckillBoard", buildSeckillBoard(snapshot));
        return Result.success(data);
    }

    private StatsSnapshot loadStatsSnapshot() {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>());
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>());
        List<SeckillProduct> seckillProducts = seckillProductMapper.selectList(new LambdaQueryWrapper<SeckillProduct>());
        List<SeckillOrder> seckillOrders = seckillOrderMapper.selectList(new LambdaQueryWrapper<SeckillOrder>());
        return new StatsSnapshot(orders, products, seckillProducts, seckillOrders);
    }

    private Map<String, Object> buildOverview(StatsSnapshot snapshot) {
        List<Order> orders = snapshot.orders();
        List<Product> products = snapshot.products();
        List<SeckillProduct> seckillProducts = snapshot.seckillProducts();
        List<SeckillOrder> seckillOrders = snapshot.seckillOrders();

        LocalDate today = LocalDate.now();
        long orderCount = orders.size();
        long todayOrderCount = orders.stream().filter(item -> isSameDate(item.getCreateTime(), today)).count();
        BigDecimal salesAmount = sumOrderAmount(orders, null);
        BigDecimal todaySalesAmount = sumOrderAmount(orders, today);
        long waitPayCount = orders.stream().filter(item -> Objects.equals(item.getPayStatus(), 0)).count();
        long waitShipCount = orders.stream().filter(item -> Objects.equals(item.getPayStatus(), 1)).count();
        long waitRefundCount = 0L;
        long onSaleProductCount = products.stream().filter(item -> Objects.equals(item.getStatus(), 1)).count();
        long activeSeckillCount = seckillProducts.stream().filter(this::isRunningSeckill).count();
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
        return data;
    }

    private Map<String, Object> buildTrend7d(List<Order> orders) {
        LocalDate today = LocalDate.now();
        List<String> dates = new ArrayList<>();
        List<Long> orderCounts = new ArrayList<>();
        List<BigDecimal> salesAmounts = new ArrayList<>();
        for (int index = 6; index >= 0; index--) {
            LocalDate date = today.minusDays(index);
            dates.add(date.format(TREND_DATE_FORMATTER));
            orderCounts.add(orders.stream().filter(item -> isSameDate(item.getCreateTime(), date)).count());
            salesAmounts.add(sumOrderAmount(orders, date));
        }
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("dates", dates);
        trend.put("orderCounts", orderCounts);
        trend.put("salesAmounts", salesAmounts);
        return trend;
    }

    private Map<String, Object> buildOrderStatusDistribution(List<Order> orders) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(buildValueItem("待支付", orders.stream().filter(item -> Objects.equals(item.getPayStatus(), 0)).count()));
        items.add(buildValueItem("已支付", orders.stream().filter(item -> Objects.equals(item.getPayStatus(), 1)).count()));
        items.add(buildValueItem("已取消", orders.stream().filter(item -> Objects.equals(item.getPayStatus(), 2)).count()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", orders.size());
        return data;
    }

    private List<Map<String, Object>> buildTopProducts(List<Order> orders, List<Product> products) {
        Map<Long, Product> productMap = products.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Product::getId, item -> item, (left, right) -> left));
        Map<Long, Long> productSales = new LinkedHashMap<>();
        Map<Long, BigDecimal> productAmounts = new LinkedHashMap<>();

        for (Order order : orders) {
            if (order.getProductId() == null || isCanceledOrder(order)) {
                continue;
            }
            productSales.put(order.getProductId(), productSales.getOrDefault(order.getProductId(), 0L) + 1L);
            productAmounts.put(order.getProductId(), productAmounts.getOrDefault(order.getProductId(), BigDecimal.ZERO)
                    .add(safeAmount(order.getAmount())));
        }

        return productSales.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Long, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> productAmounts.getOrDefault(entry.getKey(), BigDecimal.ZERO), Comparator.reverseOrder()))
                .limit(5)
                .map(entry -> {
                    Product product = productMap.get(entry.getKey());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", entry.getKey());
                    row.put("name", product == null ? ("商品" + entry.getKey()) : product.getName());
                    row.put("orderCount", entry.getValue());
                    row.put("salesAmount", productAmounts.getOrDefault(entry.getKey(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
                    return row;
                })
                .toList();
    }

    private Map<String, Object> buildSeckillBoard(StatsSnapshot snapshot) {
        List<SeckillProduct> seckillProducts = snapshot.seckillProducts();
        List<SeckillOrder> seckillOrders = snapshot.seckillOrders();
        List<Order> orders = snapshot.orders();
        Map<Long, Product> productMap = snapshot.products().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(Product::getId, item -> item, (left, right) -> left));

        long runningActivityCount = seckillProducts.stream().filter(this::isRunningSeckill).count();
        long endedActivityCount = seckillProducts.stream().filter(this::isEndedSeckill).count();
        long lowStockCount = seckillProducts.stream()
                .filter(this::isRunningSeckill)
                .filter(item -> safeStock(item.getSeckillStock()) <= LOW_STOCK_THRESHOLD)
                .count();
        double conversionRate = orders.isEmpty() ? 0D : ((double) seckillOrders.size() / (double) orders.size()) * 100D;

        List<Map<String, Object>> lowStockProducts = seckillProducts.stream()
                .filter(this::isRunningSeckill)
                .filter(item -> safeStock(item.getSeckillStock()) <= LOW_STOCK_THRESHOLD)
                .sorted(Comparator.comparingInt(item -> safeStock(item.getSeckillStock())))
                .limit(5)
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", item.getProductId());
                    row.put("name", product == null ? ("商品" + item.getProductId()) : product.getName());
                    row.put("stock", safeStock(item.getSeckillStock()));
                    row.put("startTime", DemoContextService.formatDateTime(item.getStartTime()));
                    row.put("endTime", DemoContextService.formatDateTime(item.getEndTime()));
                    return row;
                })
                .toList();

        Map<String, Object> board = new LinkedHashMap<>();
        board.put("orderCount", seckillOrders.size());
        board.put("conversionRate", roundRate(conversionRate));
        board.put("runningActivityCount", runningActivityCount);
        board.put("endedActivityCount", endedActivityCount);
        board.put("lowStockCount", lowStockCount);
        board.put("lowStockProducts", lowStockProducts);
        return board;
    }

    private BigDecimal sumOrderAmount(List<Order> orders, LocalDate date) {
        return orders.stream()
                .filter(item -> !isCanceledOrder(item))
                .filter(item -> date == null || isSameDate(item.getCreateTime(), date))
                .map(item -> safeAmount(item.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isRunningSeckill(SeckillProduct product) {
        if (!Objects.equals(product.getStatus(), 1)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean started = product.getStartTime() == null || !now.isBefore(product.getStartTime());
        boolean notEnded = product.getEndTime() == null || !now.isAfter(product.getEndTime());
        return started && notEnded;
    }

    private boolean isEndedSeckill(SeckillProduct product) {
        return product.getEndTime() != null && LocalDateTime.now().isAfter(product.getEndTime());
    }

    private static boolean isSameDate(LocalDateTime value, LocalDate date) {
        return value != null && Objects.equals(value.toLocalDate(), date);
    }

    private static boolean isCanceledOrder(Order order) {
        return Objects.equals(order.getPayStatus(), 2);
    }

    private static BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int safeStock(Integer value) {
        return value == null ? 0 : value;
    }

    private static Map<String, Object> buildValueItem(String name, long value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    private static double roundRate(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private record StatsSnapshot(List<Order> orders,
                                 List<Product> products,
                                 List<SeckillProduct> seckillProducts,
                                 List<SeckillOrder> seckillOrders) {
    }
}
