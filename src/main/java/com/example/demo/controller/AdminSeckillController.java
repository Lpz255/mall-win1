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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 鍚庡彴绉掓潃绠＄悊鎺ュ彛銆? */
@RestController
@RequestMapping("/admin/seckill")
public class AdminSeckillController {

    private final DemoContextService demoContextService;
    private final SeckillProductMapper seckillProductMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final ProductMapper productMapper;

    public AdminSeckillController(DemoContextService demoContextService,
                                  SeckillProductMapper seckillProductMapper,
                                  SeckillOrderMapper seckillOrderMapper,
                                  ProductMapper productMapper) {
        this.demoContextService = demoContextService;
        this.seckillProductMapper = seckillProductMapper;
        this.seckillOrderMapper = seckillOrderMapper;
        this.productMapper = productMapper;
    }

    @GetMapping("/list")
    public Result list(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestParam Map<String, Object> params) {
        demoContextService.getAdminPermissions(authorization);

        int pageNum = Math.max(DemoContextService.toInt(params.get("pageNum"), 1), 1);
        int pageSize = Math.max(DemoContextService.toInt(params.get("pageSize"), 10), 1);
        Long productId = DemoContextService.toLong(params.get("productId"));
        String status = stringValue(params.get("status"));

        List<SeckillProduct> all = seckillProductMapper.selectList(new LambdaQueryWrapper<SeckillProduct>()
                .orderByDesc(SeckillProduct::getStartTime));
        Map<Long, Product> productMap = loadProductMap(all.stream().map(SeckillProduct::getProductId).toList());
        Map<Long, Long> salesMap = buildSalesMap();

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (SeckillProduct item : all) {
            if (productId != null && !Objects.equals(productId, item.getProductId())) {
                continue;
            }
            String runtimeStatus = buildStatus(item);
            if (StringUtils.hasText(status) && !Objects.equals(status.toLowerCase(), runtimeStatus)) {
                continue;
            }
            filtered.add(buildRow(item, productMap.get(item.getProductId()), salesMap.getOrDefault(item.getProductId(), 0L)));
        }

        int total = filtered.size();
        int start = (pageNum - 1) * pageSize;
        List<Map<String, Object>> rows;
        if (start >= total) {
            rows = List.of();
        } else {
            int end = Math.min(total, start + pageSize);
            rows = filtered.subList(start, end);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("records", rows);
        data.put("total", total);
        data.put("count", total);
        return Result.success(data);
    }

    @Transactional
    @PostMapping("/create")
    public Result create(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);

        Long productId = DemoContextService.toLong(body.get("productId"));
        String productName = stringValue(body.get("productName"));
        if (productId == null || productId <= 0) {
            throw new BusinessException(400, "鍟嗗搧ID涓嶈兘涓虹┖");
        }

        Product product = productMapper.selectById(productId);
        if (product == null) {
            product = new Product();
            product.setId(productId);
            product.setName(StringUtils.hasText(productName) ? productName : ("鍟嗗搧" + productId));
            product.setCategoryId(1L);
            product.setPrice(requirePrice(body.get("seckillPrice")).multiply(new BigDecimal("1.2")));
            product.setStock(Math.max(DemoContextService.toInt(body.get("stock"), 10), 10));
            product.setStatus(1);
            product.setCreateTime(LocalDateTime.now());
            product.setImageUrl("https://via.placeholder.com/640x360?text=Product");
            productMapper.insert(product);
        }

        SeckillProduct entity = new SeckillProduct();
        entity.setProductId(productId);
        entity.setSeckillPrice(requirePrice(body.get("seckillPrice")));
        entity.setSeckillStock(Math.max(DemoContextService.toInt(body.get("stock"), 10), 1));
        entity.setStartTime(requireDateTime(body.get("startTime"), "开始时间不能为空"));
        entity.setEndTime(requireDateTime(body.get("endTime"), "缁撴潫鏃堕棿涓嶈兘涓虹┖"));
        if (entity.getEndTime().isBefore(entity.getStartTime()) || entity.getEndTime().isEqual(entity.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        entity.setStatus(toRunningFlag(body.get("status")));

        int insertRows = seckillProductMapper.insert(entity);
        if (insertRows <= 0) {
            throw new BusinessException(500, "绉掓潃娲诲姩鏂板澶辫触");
        }

        return Result.success(buildRow(entity, product, 0L));
    }

    @Transactional
    @PostMapping("/update")
    public Result update(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);
        Long id = DemoContextService.toLong(body.get("id"));
        if (id == null || id <= 0) {
            throw new BusinessException(400, "娲诲姩ID涓嶈兘涓虹┖");
        }
        SeckillProduct entity = seckillProductMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "活动不存在");
        }

        if (body.containsKey("productId")) {
            entity.setProductId(DemoContextService.toLong(body.get("productId")));
        }
        if (body.containsKey("seckillPrice")) {
            entity.setSeckillPrice(requirePrice(body.get("seckillPrice")));
        }
        if (body.containsKey("stock")) {
            entity.setSeckillStock(Math.max(DemoContextService.toInt(body.get("stock"), 1), 1));
        }
        if (body.containsKey("startTime")) {
            entity.setStartTime(requireDateTime(body.get("startTime"), "开始时间不能为空"));
        }
        if (body.containsKey("endTime")) {
            entity.setEndTime(requireDateTime(body.get("endTime"), "缁撴潫鏃堕棿涓嶈兘涓虹┖"));
        }
        if (body.containsKey("status")) {
            entity.setStatus(toRunningFlag(body.get("status")));
        }
        if (entity.getStartTime() != null && entity.getEndTime() != null
                && (entity.getEndTime().isBefore(entity.getStartTime()) || entity.getEndTime().isEqual(entity.getStartTime()))) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }

        int updateRows = seckillProductMapper.updateById(entity);
        if (updateRows <= 0) {
            throw new BusinessException(500, "娲诲姩鏇存柊澶辫触");
        }
        Product product = entity.getProductId() == null ? null : productMapper.selectById(entity.getProductId());
        Long sales = buildSalesMap().getOrDefault(entity.getProductId(), 0L);
        return Result.success(buildRow(entity, product, sales));
    }

    @Transactional
    @PostMapping("/start")
    public Result start(@RequestHeader(value = "Authorization", required = false) String authorization,
                        @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);
        Long id = DemoContextService.toLong(body.get("id"));
        if (id == null || id <= 0) {
            throw new BusinessException(400, "娲诲姩ID涓嶈兘涓虹┖");
        }
        int rows = seckillProductMapper.update(null, new LambdaUpdateWrapper<SeckillProduct>()
                .eq(SeckillProduct::getId, id)
                .set(SeckillProduct::getStatus, 1));
        if (rows <= 0) {
            throw new BusinessException(404, "活动不存在");
        }
        return Result.success();
    }

    @Transactional
    @PostMapping("/stop")
    public Result stop(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);
        Long id = DemoContextService.toLong(body.get("id"));
        if (id == null || id <= 0) {
            throw new BusinessException(400, "娲诲姩ID涓嶈兘涓虹┖");
        }
        int rows = seckillProductMapper.update(null, new LambdaUpdateWrapper<SeckillProduct>()
                .eq(SeckillProduct::getId, id)
                .set(SeckillProduct::getStatus, 0));
        if (rows <= 0) {
            throw new BusinessException(404, "活动不存在");
        }
        return Result.success();
    }

    @Transactional
    @PostMapping("/delete")
    public Result delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);
        Long id = DemoContextService.toLong(body.get("id"));
        if (id == null || id <= 0) {
            throw new BusinessException(400, "娲诲姩ID涓嶈兘涓虹┖");
        }
        int rows = seckillProductMapper.deleteById(id);
        if (rows <= 0) {
            throw new BusinessException(404, "活动不存在");
        }
        return Result.success();
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
            sales.put(order.getProductId(), sales.getOrDefault(order.getProductId(), 0L) + 1L);
        }
        return sales;
    }

    private static Map<String, Object> buildRow(SeckillProduct entity, Product product, Long sales) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("productId", entity.getProductId());
        row.put("productName", product == null ? ("鍟嗗搧" + entity.getProductId()) : product.getName());
        row.put("name", product == null ? ("鍟嗗搧" + entity.getProductId()) : product.getName());
        row.put("seckillPrice", safePrice(entity.getSeckillPrice()));
        row.put("price", safePrice(entity.getSeckillPrice()));
        row.put("stock", entity.getSeckillStock() == null ? 0 : entity.getSeckillStock());
        row.put("sales", sales == null ? 0 : sales);
        row.put("status", buildStatus(entity));
        row.put("startTime", DemoContextService.formatDateTime(entity.getStartTime()));
        row.put("endTime", DemoContextService.formatDateTime(entity.getEndTime()));
        return row;
    }

    private static String buildStatus(SeckillProduct entity) {
        LocalDateTime now = LocalDateTime.now();
        if (entity.getEndTime() != null && now.isAfter(entity.getEndTime())) {
            return "ended";
        }
        if (entity.getStartTime() != null && now.isBefore(entity.getStartTime())) {
            return "draft";
        }
        if (Objects.equals(entity.getStatus(), 1)) {
            return "running";
        }
        return "stopped";
    }

    private static int toRunningFlag(Object value) {
        String text = stringValue(value).toLowerCase();
        return "running".equals(text) || "on".equals(text) || "1".equals(text) ? 1 : 0;
    }

    private static LocalDateTime requireDateTime(Object value, String message) {
        LocalDateTime dateTime = DemoContextService.parseDateTime(value);
        if (dateTime == null) {
            throw new BusinessException(400, message);
        }
        return dateTime;
    }

    private static BigDecimal requirePrice(Object value) {
        BigDecimal price = DemoContextService.toBigDecimal(value, null);
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "绉掓潃浠锋牸蹇呴』澶т簬0");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static BigDecimal safePrice(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}

