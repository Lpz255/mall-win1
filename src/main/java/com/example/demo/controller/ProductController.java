package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductDetail;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.mapper.ProductDetailMapper;
import com.example.demo.support.DemoContextService;
import com.example.demo.utils.BusinessException;
import com.example.demo.utils.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品接口。
 */
@RestController
@RequestMapping("/product")
public class ProductController {

    private static final int STATUS_ON_SALE = 1;

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final ProductDetailMapper productDetailMapper;

    public ProductController(ProductMapper productMapper,
                             OrderMapper orderMapper,
                             ProductDetailMapper productDetailMapper) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.productDetailMapper = productDetailMapper;
    }

    @GetMapping("/banner")
    public Result banner() {
        List<Product> products = queryOnSaleProducts();
        List<Map<String, Object>> data = products.stream().limit(5).map(product -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", product.getId());
            item.put("title", product.getName());
            item.put("image", normalizeImage(product.getImageUrl()));
            item.put("link", "/web/product/detail/" + product.getId());
            return item;
        }).toList();
        return Result.success(data);
    }

    @GetMapping("/category")
    public Result category() {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .select(Product::getCategoryId));
        List<Long> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        List<Map<String, Object>> categories = new ArrayList<>();
        if (categoryIds.isEmpty()) {
            categories.add(buildCategory(1L));
            categories.add(buildCategory(2L));
            categories.add(buildCategory(3L));
            categories.add(buildCategory(4L));
        } else {
            for (Long categoryId : categoryIds) {
                categories.add(buildCategory(categoryId));
            }
        }
        return Result.success(categories);
    }

    @GetMapping("/hot")
    public Result hot(@RequestParam(value = "size", required = false) Integer size) {
        int limit = size == null || size <= 0 ? 8 : Math.min(size, 50);
        List<Map<String, Object>> rows = listProductViews(true);
        rows.sort(Comparator.comparing(item -> DemoContextService.toInt(item.get("sales"), 0), Comparator.reverseOrder()));
        return Result.success(rows.stream().limit(limit).toList());
    }

    @GetMapping("/list")
    public Result list(@RequestParam Map<String, Object> params) {
        int pageNum = Math.max(DemoContextService.toInt(params.get("pageNum"), 1), 1);
        int pageSize = Math.max(DemoContextService.toInt(params.get("pageSize"), 10), 1);
        String keyword = stringValue(params.get("keyword"));
        Long categoryId = DemoContextService.toLong(params.get("categoryId"));
        BigDecimal minPrice = DemoContextService.toBigDecimal(params.get("minPrice"), null);
        BigDecimal maxPrice = DemoContextService.toBigDecimal(params.get("maxPrice"), null);
        String statusText = stringValue(params.get("status"));
        String sortField = stringValue(params.get("sortField"));
        String sortOrder = stringValue(params.get("sortOrder"));

        boolean filterOnSale = !StringUtils.hasText(statusText) || "on_sale".equalsIgnoreCase(statusText);
        boolean filterOffSale = "off_sale".equalsIgnoreCase(statusText);

        List<Map<String, Object>> all = listProductViews(StringUtils.hasText(statusText));
        List<Map<String, Object>> filtered = all.stream().filter(item -> {
            if (filterOnSale && !"on_sale".equalsIgnoreCase(stringValue(item.get("status")))) {
                return false;
            }
            if (filterOffSale && !"off_sale".equalsIgnoreCase(stringValue(item.get("status")))) {
                return false;
            }
            if (StringUtils.hasText(keyword)) {
                String key = keyword.trim().toLowerCase();
                String name = stringValue(item.get("name")).toLowerCase();
                String idText = String.valueOf(item.get("id"));
                if (!name.contains(key) && !idText.contains(key)) {
                    return false;
                }
            }
            if (categoryId != null && !Objects.equals(categoryId, DemoContextService.toLong(item.get("categoryId")))) {
                return false;
            }
            if (minPrice != null) {
                BigDecimal price = DemoContextService.toBigDecimal(item.get("price"), BigDecimal.ZERO);
                if (price.compareTo(minPrice) < 0) {
                    return false;
                }
            }
            if (maxPrice != null) {
                BigDecimal price = DemoContextService.toBigDecimal(item.get("price"), BigDecimal.ZERO);
                if (price.compareTo(maxPrice) > 0) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        applySort(filtered, sortField, sortOrder);

        int total = filtered.size();
        int start = (pageNum - 1) * pageSize;
        List<Map<String, Object>> pageList;
        if (start >= total) {
            pageList = List.of();
        } else {
            int end = Math.min(total, start + pageSize);
            pageList = filtered.subList(start, end);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", pageList);
        data.put("records", pageList);
        data.put("total", total);
        data.put("count", total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return Result.success(data);
    }

    @GetMapping("/detail")
    public Result detailByQuery(@RequestParam("id") Long id) {
        return Result.success(queryDetail(id));
    }

    @GetMapping("/detail/{id}")
    public Result detailByPath(@PathVariable("id") Long id) {
        return Result.success(queryDetail(id));
    }

    private Map<String, Object> queryDetail(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(400, "商品ID不合法");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        String detailContent = queryDetailContent(product.getId());
        return buildProductView(product, buildSalesMap().getOrDefault(product.getId(), 0), true, detailContent);
    }

    private List<Product> queryOnSaleProducts() {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, STATUS_ON_SALE)
                .orderByDesc(Product::getCreateTime));
    }

    private List<Map<String, Object>> listProductViews(boolean includeOffSale) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (!includeOffSale) {
            wrapper.eq(Product::getStatus, STATUS_ON_SALE);
        }
        wrapper.orderByDesc(Product::getCreateTime);
        List<Product> products = productMapper.selectList(wrapper);
        Map<Long, Integer> salesMap = buildSalesMap();
        Map<Long, String> detailMap = buildDetailMap(products);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Product product : products) {
            result.add(buildProductView(
                    product,
                    salesMap.getOrDefault(product.getId(), 0),
                    false,
                    detailMap.get(product.getId())
            ));
        }
        return result;
    }

    private Map<Long, Integer> buildSalesMap() {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .select(Order::getProductId, Order::getPayStatus));
        Map<Long, Integer> salesMap = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.getProductId() == null) {
                continue;
            }
            // pay_status=2 为取消订单，不记销量。
            if (Objects.equals(order.getPayStatus(), 2)) {
                continue;
            }
            salesMap.put(order.getProductId(), salesMap.getOrDefault(order.getProductId(), 0) + 1);
        }
        return salesMap;
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

    private Map<String, Object> buildProductView(Product product, int sales, boolean detail, String detailContent) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", product.getId());
        row.put("productId", product.getId());
        row.put("name", product.getName());
        row.put("productName", product.getName());
        row.put("price", safePrice(product.getPrice()));
        row.put("salePrice", safePrice(product.getPrice()));
        row.put("originalPrice", safeOriginalPrice(product.getPrice()));
        row.put("stock", product.getStock() == null ? 0 : product.getStock());
        row.put("sales", sales);
        row.put("categoryId", product.getCategoryId());
        row.put("categoryName", categoryName(product.getCategoryId()));
        row.put("image", normalizeImage(product.getImageUrl()));
        row.put("cover", normalizeImage(product.getImageUrl()));
        String mergedDescription = StringUtils.hasText(detailContent) ? detailContent : product.getDescription();
        row.put("description", normalizeDescription(mergedDescription));
        row.put("detailContent", normalizeDescription(mergedDescription));
        row.put("status", Objects.equals(product.getStatus(), STATUS_ON_SALE) ? "on_sale" : "off_sale");
        row.put("createTime", DemoContextService.formatDateTime(product.getCreateTime()));
        if (detail) {
            row.put("subtitle", "品质好货，售后无忧");
            row.put("images", List.of(normalizeImage(product.getImageUrl())));
        }
        return row;
    }

    private static String normalizeImage(String imageUrl) {
        if (StringUtils.hasText(imageUrl)) {
            return imageUrl;
        }
        return "https://via.placeholder.com/640x640?text=Product";
    }

    private static String normalizeDescription(String description) {
        if (StringUtils.hasText(description)) {
            return description;
        }
        return "精选商品，支持快速下单与秒杀活动。";
    }

    private static Map<String, Object> buildCategory(Long categoryId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", categoryId);
        row.put("name", categoryName(categoryId));
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

    private static BigDecimal safePrice(BigDecimal price) {
        return price == null ? BigDecimal.ZERO : price.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal safeOriginalPrice(BigDecimal price) {
        BigDecimal base = price == null ? BigDecimal.ZERO : price;
        return base.multiply(new BigDecimal("1.20")).setScale(2, RoundingMode.HALF_UP);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void applySort(List<Map<String, Object>> data, String sortField, String sortOrder) {
        if (!StringUtils.hasText(sortField)) {
            return;
        }
        Comparator<Map<String, Object>> comparator;
        switch (sortField) {
            case "price" -> comparator = Comparator.comparing(item -> DemoContextService.toBigDecimal(item.get("price"), BigDecimal.ZERO));
            case "sales" -> comparator = Comparator.comparing(item -> DemoContextService.toInt(item.get("sales"), 0));
            default -> comparator = Comparator.comparing(item -> String.valueOf(item.get("createTime")));
        }
        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }
        data.sort(comparator);
    }
}
