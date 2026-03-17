package com.example.demo.controller;

import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.support.DemoContextService;
import com.example.demo.utils.BusinessException;
import com.example.demo.utils.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车接口。
 */
@RestController
@RequestMapping("/cart")
public class CartController {

    private final DemoContextService demoContextService;
    private final ProductMapper productMapper;

    public CartController(DemoContextService demoContextService, ProductMapper productMapper) {
        this.demoContextService = demoContextService;
        this.productMapper = productMapper;
    }

    @GetMapping("/list")
    public Result list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = demoContextService.requireUserId(authorization);
        List<DemoContextService.CartEntry> entries = demoContextService.listCart(userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DemoContextService.CartEntry entry : entries) {
            rows.add(buildCartRow(entry));
        }
        return Result.success(rows);
    }

    @PostMapping("/add")
    public Result add(@RequestHeader(value = "Authorization", required = false) String authorization,
                      @RequestBody Map<String, Object> body) {
        Long userId = demoContextService.requireUserId(authorization);
        Long productId = DemoContextService.toLong(body.get("productId"));
        int quantity = Math.max(DemoContextService.toInt(body.get("quantity"), 1), 1);
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        demoContextService.addCartItem(userId, productId, quantity);
        return Result.success();
    }

    @PostMapping("/update")
    public Result update(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        Long userId = demoContextService.requireUserId(authorization);
        Long itemId = DemoContextService.toLong(body.get("id"));
        if (itemId == null) {
            itemId = DemoContextService.toLong(body.get("productId"));
        }
        int quantity = Math.max(DemoContextService.toInt(body.get("quantity"), 1), 1);
        demoContextService.updateCartItem(userId, itemId, quantity);
        return Result.success();
    }

    @PostMapping("/checked")
    public Result checked(@RequestHeader(value = "Authorization", required = false) String authorization,
                          @RequestBody Map<String, Object> body) {
        Long userId = demoContextService.requireUserId(authorization);
        Long itemId = DemoContextService.toLong(body.get("id"));
        if (itemId == null) {
            itemId = DemoContextService.toLong(body.get("productId"));
        }
        boolean checked = Boolean.parseBoolean(String.valueOf(body.getOrDefault("checked", "true")));
        demoContextService.setCartChecked(userId, itemId, checked);
        return Result.success();
    }

    @PostMapping("/remove")
    public Result remove(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        Long userId = demoContextService.requireUserId(authorization);
        Long itemId = DemoContextService.toLong(body.get("id"));
        if (itemId == null) {
            itemId = DemoContextService.toLong(body.get("productId"));
        }
        demoContextService.removeCartItem(userId, itemId);
        return Result.success();
    }

    @PostMapping("/clear")
    public Result clear(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Long userId = demoContextService.requireUserId(authorization);
        demoContextService.clearCart(userId);
        return Result.success();
    }

    private Map<String, Object> buildCartRow(DemoContextService.CartEntry entry) {
        Product product = productMapper.selectById(entry.getProductId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entry.getProductId());
        row.put("productId", entry.getProductId());
        row.put("quantity", entry.getQuantity());
        row.put("checked", entry.getChecked());
        row.put("isChecked", entry.getChecked());
        row.put("selected", entry.getChecked());
        row.put("updateTime", DemoContextService.formatDateTime(entry.getUpdateTime()));
        if (product != null) {
            row.put("name", product.getName());
            row.put("productName", product.getName());
            row.put("price", safePrice(product.getPrice()));
            row.put("salePrice", safePrice(product.getPrice()));
            row.put("image", normalizeImage(product.getImageUrl()));
            row.put("cover", normalizeImage(product.getImageUrl()));
            row.put("subtitle", "购物车商品");
            row.put("description", "已加入购物车");
        } else {
            row.put("name", "商品" + entry.getProductId());
            row.put("productName", "商品" + entry.getProductId());
            row.put("price", BigDecimal.ZERO);
            row.put("salePrice", BigDecimal.ZERO);
            row.put("image", "https://via.placeholder.com/120x120?text=Cart");
            row.put("cover", "https://via.placeholder.com/120x120?text=Cart");
            row.put("subtitle", "");
            row.put("description", "");
        }
        return row;
    }

    private static BigDecimal safePrice(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalizeImage(String imageUrl) {
        if (StringUtils.hasText(imageUrl)) {
            return imageUrl;
        }
        return "https://via.placeholder.com/120x120?text=Cart";
    }
}
