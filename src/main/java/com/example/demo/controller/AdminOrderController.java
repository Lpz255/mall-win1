package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.support.DemoContextService;
import com.example.demo.support.DemoContextService.UserRecord;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 鍚庡彴璁㈠崟绠＄悊鎺ュ彛銆? */
@RestController
@RequestMapping("/admin/order")
public class AdminOrderController {

    private final DemoContextService demoContextService;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;

    public AdminOrderController(DemoContextService demoContextService,
                                OrderMapper orderMapper,
                                ProductMapper productMapper) {
        this.demoContextService = demoContextService;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
    }

    @GetMapping("/list")
    public Result list(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestParam Map<String, Object> params) {
        demoContextService.getAdminPermissions(authorization);

        int pageNum = Math.max(DemoContextService.toInt(params.get("pageNum"), 1), 1);
        int pageSize = Math.max(DemoContextService.toInt(params.get("pageSize"), 10), 1);
        String orderNo = stringValue(params.get("orderNo"));
        String userKeyword = stringValue(params.get("userKeyword"));
        String status = stringValue(params.get("status"));

        List<Order> all = orderMapper.selectList(new LambdaQueryWrapper<Order>().orderByDesc(Order::getCreateTime));
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Order order : all) {
            Map<String, Object> row = buildRow(order);
            if (StringUtils.hasText(orderNo) && !stringValue(row.get("orderNo")).contains(orderNo)) {
                continue;
            }
            if (StringUtils.hasText(userKeyword)) {
                String keyword = userKeyword.toLowerCase();
                String userName = stringValue(row.get("userName")).toLowerCase();
                String phone = stringValue(row.get("phone")).toLowerCase();
                if (!userName.contains(keyword) && !phone.contains(keyword)) {
                    continue;
                }
            }
            if (StringUtils.hasText(status) && !Objects.equals(status.toUpperCase(), stringValue(row.get("status")).toUpperCase())) {
                continue;
            }
            filtered.add(row);
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

    @GetMapping("/detail")
    public Result detail(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestParam("orderId") Long orderId) {
        demoContextService.getAdminPermissions(authorization);
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        return Result.success(buildDetail(order));
    }

    @Transactional
    @PostMapping("/status")
    public Result status(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);
        Long orderId = DemoContextService.toLong(body.get("orderId"));
        String status = stringValue(body.get("status")).toUpperCase();
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(400, "璁㈠崟ID涓嶈兘涓虹┖");
        }
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(400, "订单状态不能为空");
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        int payStatus = mapPayStatus(status);
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .set(Order::getPayStatus, payStatus));
        demoContextService.updateOrderStatus(orderId, status);
        return Result.success();
    }

    @Transactional
    @PostMapping("/refund")
    public Result refund(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        demoContextService.getAdminPermissions(authorization);
        Long orderId = DemoContextService.toLong(body.get("orderId"));
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(400, "璁㈠崟ID涓嶈兘涓虹┖");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }

        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .set(Order::getPayStatus, 2));
        demoContextService.updateOrderStatus(orderId, "CANCELED");
        demoContextService.updateRefundStatus(orderId, "SUCCESS");
        return Result.success();
    }

    private Map<String, Object> buildRow(Order order) {
        Map<String, Object> row = new LinkedHashMap<>();
        DemoContextService.OrderExtra extra = demoContextService.getOrderExtra(order.getId());
        UserRecord user = demoContextService.findUserById(order.getUserId());

        row.put("id", order.getId());
        row.put("orderId", order.getId());
        row.put("orderNo", order.getOrderNo());
        row.put("status", extra != null && StringUtils.hasText(extra.getStatus()) ? extra.getStatus() : mapStatus(order.getPayStatus()));
        row.put("refundStatus", extra != null && StringUtils.hasText(extra.getRefundStatus()) ? extra.getRefundStatus() : "NONE");
        row.put("amount", order.getAmount());
        row.put("totalAmount", order.getAmount());
        row.put("createTime", DemoContextService.formatDateTime(order.getCreateTime()));
        row.put("address", extra != null && StringUtils.hasText(extra.getAddress()) ? extra.getAddress() : order.getDeliveryAddress());
        row.put("remark", extra == null ? "" : extra.getRemark());
        row.put("userName", user == null ? ("鐢ㄦ埛" + order.getUserId()) : user.getName());
        row.put("phone", user == null ? "" : user.getPhone());
        return row;
    }

    private Map<String, Object> buildDetail(Order order) {
        Map<String, Object> row = buildRow(order);
        DemoContextService.OrderExtra extra = demoContextService.getOrderExtra(order.getId());
        if (extra != null && extra.getItems() != null && !extra.getItems().isEmpty()) {
            row.put("items", extra.getItems());
            return row;
        }

        Product product = order.getProductId() == null ? null : productMapper.selectById(order.getProductId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", order.getProductId());
        item.put("name", product == null ? "鍟嗗搧" + order.getProductId() : product.getName());
        item.put("price", product == null ? (order.getAmount() == null ? BigDecimal.ZERO : order.getAmount()) : product.getPrice());
        item.put("quantity", 1);
        row.put("items", List.of(item));
        return row;
    }

    private static int mapPayStatus(String status) {
        return switch (status.toUpperCase()) {
            case "PAYED", "SHIPPED", "DONE" -> 1;
            case "CANCELED" -> 2;
            default -> 0;
        };
    }

    private static String mapStatus(Integer payStatus) {
        if (Objects.equals(payStatus, 1)) {
            return "PAYED";
        }
        if (Objects.equals(payStatus, 2)) {
            return "CANCELED";
        }
        return "WAIT_PAY";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

