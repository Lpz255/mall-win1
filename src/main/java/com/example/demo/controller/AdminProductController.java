package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductDetail;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.ProductDetailMapper;
import com.example.demo.service.MinioStorageService;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 鍚庡彴鍟嗗搧绠＄悊鎺ュ彛銆? */
@RestController
@RequestMapping("/admin/product")
public class AdminProductController {

    private final DemoContextService demoContextService;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final ProductDetailMapper productDetailMapper;
    private final MinioStorageService minioStorageService;

    public AdminProductController(DemoContextService demoContextService,
                                  ProductMapper productMapper,
                                  OrderMapper orderMapper,
                                  ProductDetailMapper productDetailMapper,
                                  MinioStorageService minioStorageService) {
        this.demoContextService = demoContextService;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.productDetailMapper = productDetailMapper;
        this.minioStorageService = minioStorageService;
    }

    @GetMapping("/list")
    public Result list(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestParam Map<String, Object> params) {
        demoContextService.getAdminPermissions(authorization);

        int pageNum = Math.max(DemoContextService.toInt(params.get("pageNum"), 1), 1);
        int pageSize = Math.max(DemoContextService.toInt(params.get("pageSize"), 10), 1);
        String keyword = stringValue(params.get("keyword"));
        Long categoryId = DemoContextService.toLong(params.get("categoryId"));
        String status = stringValue(params.get("status"));

        List<Product> all = productMapper.selectList(new LambdaQueryWrapper<Product>().orderByDesc(Product::getCreateTime));
        Map<Long, String> detailMap = buildDetailMap(all);
        Map<Long, Integer> salesMap = buildSalesMap();
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Product product : all) {
            if (StringUtils.hasText(keyword)) {
                String text = keyword.toLowerCase();
                if (!String.valueOf(product.getId()).contains(text)
                        && (product.getName() == null || !product.getName().toLowerCase().contains(text))) {
                    continue;
                }
            }
            if (categoryId != null && !Objects.equals(categoryId, product.getCategoryId())) {
                continue;
            }
            if ("on_sale".equalsIgnoreCase(status) && !Objects.equals(product.getStatus(), 1)) {
                continue;
            }
            if ("off_sale".equalsIgnoreCase(status) && !Objects.equals(product.getStatus(), 0)) {
                continue;
            }
            filtered.add(buildRow(product, salesMap.getOrDefault(product.getId(), 0), detailMap.get(product.getId())));
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

        Product product = new Product();
        product.setName(requireText(body.get("name"), "鍟嗗搧鍚嶇О涓嶈兘涓虹┖"));
        product.setCategoryId(requireLong(body.get("categoryId"), "鍟嗗搧鍒嗙被涓嶈兘涓虹┖"));
        product.setPrice(requirePrice(body.get("price")));
        product.setStock(Math.max(DemoContextService.toInt(body.get("stock"), 1), 1));
        product.setStatus(toProductStatus(body.get("status")));
        product.setImageUrl(stringValue(body.get("image")));
        product.setDescription(stringValue(body.get("description")));
        product.setCreateTime(LocalDateTime.now());

        int insertRows = productMapper.insert(product);
        if (insertRows <= 0) {
            throw new BusinessException(500, "鍟嗗搧鏂板澶辫触");
        }
        saveProductDetail(product.getId(), stringValue(body.get("description")));
        return Result.success(buildRow(product, 0, queryDetailContent(product.getId())));
    }

    @Transactional
    @PostMapping("/update")
    public Result update(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);
        Long id = requireLong(body.get("id"), "鍟嗗搧ID涓嶈兘涓虹┖");
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }

        if (body.containsKey("name")) {
            product.setName(requireText(body.get("name"), "鍟嗗搧鍚嶇О涓嶈兘涓虹┖"));
        }
        if (body.containsKey("categoryId")) {
            product.setCategoryId(requireLong(body.get("categoryId"), "鍟嗗搧鍒嗙被涓嶈兘涓虹┖"));
        }
        if (body.containsKey("price")) {
            product.setPrice(requirePrice(body.get("price")));
        }
        if (body.containsKey("stock")) {
            product.setStock(Math.max(DemoContextService.toInt(body.get("stock"), 1), 1));
        }
        if (body.containsKey("status")) {
            product.setStatus(toProductStatus(body.get("status")));
        }
        if (body.containsKey("image")) {
            product.setImageUrl(stringValue(body.get("image")));
        }
        if (body.containsKey("description")) {
            product.setDescription(stringValue(body.get("description")));
        }

        int updateRows = productMapper.updateById(product);
        if (updateRows <= 0) {
            throw new BusinessException(500, "鍟嗗搧鏇存柊澶辫触");
        }
        if (body.containsKey("description")) {
            saveProductDetail(product.getId(), stringValue(body.get("description")));
        }
        return Result.success(buildRow(
                product,
                buildSalesMap().getOrDefault(product.getId(), 0),
                queryDetailContent(product.getId())
        ));
    }

    @Transactional
    @PostMapping("/delete")
    public Result delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);
        Long id = requireLong(body.get("id"), "鍟嗗搧ID涓嶈兘涓虹┖");
        int deleteRows = productMapper.deleteById(id);
        if (deleteRows <= 0) {
            throw new BusinessException(404, "商品不存在或已删除");
        }
        return Result.success();
    }

    @Transactional
    @PostMapping("/status")
    public Result status(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);
        Long id = requireLong(body.get("id"), "鍟嗗搧ID涓嶈兘涓虹┖");
        int status = toProductStatus(body.get("status"));

        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .eq(Product::getId, id)
                .set(Product::getStatus, status));
        if (rows <= 0) {
            throw new BusinessException(404, "商品不存在");
        }
        Product product = productMapper.selectById(id);
        return Result.success(buildRow(
                product,
                buildSalesMap().getOrDefault(product.getId(), 0),
                queryDetailContent(product.getId())
        ));
    }

    @PostMapping("/upload-image")
    public Result uploadImage(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @RequestParam("file") MultipartFile file) {
        demoContextService.getAdminPermissions(authorization);
        return Result.success(minioStorageService.uploadProductImage(file));
    }

    private Map<Long, Integer> buildSalesMap() {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>().select(Order::getProductId, Order::getPayStatus));
        Map<Long, Integer> sales = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.getProductId() == null || Objects.equals(order.getPayStatus(), 2)) {
                continue;
            }
            sales.put(order.getProductId(), sales.getOrDefault(order.getProductId(), 0) + 1);
        }
        return sales;
    }

    private Map<Long, String> buildDetailMap(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Map.of();
        }
        Set<Long> productIds = products.stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductDetail> details = productDetailMapper.selectList(
                new LambdaQueryWrapper<ProductDetail>().in(ProductDetail::getProductId, productIds)
        );
        Map<Long, String> result = new LinkedHashMap<>();
        for (ProductDetail detail : details) {
            result.put(detail.getProductId(), stringValue(detail.getDetailContent()));
        }
        return result;
    }

    private String queryDetailContent(Long productId) {
        if (productId == null) {
            return "";
        }
        ProductDetail detail = productDetailMapper.selectOne(new LambdaQueryWrapper<ProductDetail>()
                .eq(ProductDetail::getProductId, productId)
                .last("limit 1"));
        return detail == null ? "" : stringValue(detail.getDetailContent());
    }

    private void saveProductDetail(Long productId, String detailContent) {
        if (productId == null) {
            return;
        }
        ProductDetail existing = productDetailMapper.selectOne(new LambdaQueryWrapper<ProductDetail>()
                .eq(ProductDetail::getProductId, productId)
                .last("limit 1"));
        if (existing == null) {
            ProductDetail detail = new ProductDetail();
            detail.setProductId(productId);
            detail.setDetailContent(detailContent);
            detail.setCreateTime(LocalDateTime.now());
            detail.setUpdateTime(LocalDateTime.now());
            productDetailMapper.insert(detail);
            return;
        }
        existing.setDetailContent(detailContent);
        existing.setUpdateTime(LocalDateTime.now());
        productDetailMapper.updateById(existing);
    }

    private static Map<String, Object> buildRow(Product product, int sales, String detailContent) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", product.getId());
        row.put("productId", product.getId());
        row.put("name", product.getName());
        row.put("productName", product.getName());
        row.put("categoryId", product.getCategoryId());
        row.put("categoryName", categoryName(product.getCategoryId()));
        row.put("price", safePrice(product.getPrice()));
        row.put("salePrice", safePrice(product.getPrice()));
        row.put("stock", product.getStock() == null ? 0 : product.getStock());
        row.put("sales", sales);
        row.put("status", Objects.equals(product.getStatus(), 1) ? "on_sale" : "off_sale");
        row.put("image", product.getImageUrl());
        row.put("cover", product.getImageUrl());
        row.put("description", StringUtils.hasText(detailContent) ? detailContent : product.getDescription());
        row.put("createTime", DemoContextService.formatDateTime(product.getCreateTime()));
        return row;
    }

    private static String categoryName(Long categoryId) {
        if (categoryId == null) {
            return "默认分类";
        }
        return switch (categoryId.intValue()) {
            case 1 -> "手机数码";
            case 2 -> "家用电器";
            case 3 -> "服饰箱包";
            case 4 -> "食品生鲜";
            case 5 -> "运动户外";
            default -> "分类" + categoryId;
        };
    }

    private static int toProductStatus(Object value) {
        String text = stringValue(value);
        if ("on_sale".equalsIgnoreCase(text) || "1".equals(text) || "true".equalsIgnoreCase(text)) {
            return 1;
        }
        if ("off_sale".equalsIgnoreCase(text) || "0".equals(text) || "false".equalsIgnoreCase(text)) {
            return 0;
        }
        return 1;
    }

    private static BigDecimal requirePrice(Object value) {
        BigDecimal price = DemoContextService.toBigDecimal(value, null);
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "浠锋牸蹇呴』澶т簬0");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private static Long requireLong(Object value, String message) {
        Long result = DemoContextService.toLong(value);
        if (result == null || result <= 0) {
            throw new BusinessException(400, message);
        }
        return result;
    }

    private static String requireText(Object value, String message) {
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(400, message);
        }
        return text;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static BigDecimal safePrice(BigDecimal price) {
        return price == null ? BigDecimal.ZERO : price.setScale(2, RoundingMode.HALF_UP);
    }
}

