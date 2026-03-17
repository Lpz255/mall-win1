package com.example.demo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.support.DemoContextService;
import com.example.demo.utils.BusinessException;
import com.example.demo.utils.Result;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import java.util.UUID;

/**
 * 订单接口。
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    private static final int PAY_STATUS_WAIT_PAY = 0;
    private static final int PAY_STATUS_PAYED = 1;
    private static final int PAY_STATUS_CANCELED = 2;

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final DemoContextService demoContextService;

    public OrderController(OrderMapper orderMapper,
                           ProductMapper productMapper,
                           DemoContextService demoContextService) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.demoContextService = demoContextService;
    }

    @Transactional
    @PostMapping("/create")
    public Result create(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody(required = false) Map<String, Object> body,
                         @RequestParam(value = "userId", required = false) Long userIdParam,
                         @RequestParam(value = "productId", required = false) Long productIdParam,
                         @RequestParam(value = "deliveryAddress", required = false) String deliveryAddressParam) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        Long userId = resolveUserId(authorization, userIdParam);
        String address = resolveAddress(payload, deliveryAddressParam);
        String remark = stringValue(payload.get("remark"));

        List<Map<String, Object>> items = resolveOrderItems(userId, payload, productIdParam);
        if (items.isEmpty()) {
            throw new BusinessException(400, "下单商品不能为空");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        Long primaryProductId = null;
        for (Map<String, Object> item : items) {
            Long productId = DemoContextService.toLong(item.get("productId"));
            int quantity = Math.max(DemoContextService.toInt(item.get("quantity"), 1), 1);
            Product product = productMapper.selectById(productId);
            if (product == null) {
                throw new BusinessException(404, "商品不存在");
            }
            if (!Objects.equals(product.getStatus(), 1)) {
                throw new BusinessException(400, "商品已下架");
            }
            if (product.getStock() == null || product.getStock() < quantity) {
                throw new BusinessException(4008, "商品库存不足");
            }

            int stockRows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .eq(Product::getId, productId)
                    .ge(Product::getStock, quantity)
                    .setSql("stock = stock - " + quantity));
            if (stockRows <= 0) {
                throw new BusinessException(4008, "商品库存不足");
            }

            BigDecimal unitPrice = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
            item.put("name", product.getName());
            item.put("price", unitPrice.setScale(2, RoundingMode.HALF_UP));

            if (primaryProductId == null) {
                primaryProductId = productId;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(buildOrderNo());
        order.setUserId(userId);
        order.setProductId(primaryProductId);
        order.setAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        order.setPayStatus(PAY_STATUS_WAIT_PAY);
        order.setDeliveryAddress(address);
        order.setCreateTime(now);
        order.setExpireTime(now.plusMinutes(30));

        int insertRows = orderMapper.insert(order);
        if (insertRows <= 0) {
            throw new BusinessException(500, "订单创建失败");
        }

        DemoContextService.OrderExtra extra = new DemoContextService.OrderExtra();
        extra.setStatus("WAIT_PAY");
        extra.setRefundStatus("NONE");
        extra.setAddress(address);
        extra.setRemark(remark);
        extra.setItems(items);
        demoContextService.saveOrderExtra(order.getId(), extra);

        if ("cart".equalsIgnoreCase(stringValue(payload.get("source")))) {
            for (Long cartItemId : toLongList(payload.get("cartItemIds"))) {
                demoContextService.removeCartItem(userId, cartItemId);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        return Result.success(result);
    }

    @GetMapping("/list")
    public Result list(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestParam(value = "userId", required = false) Long userIdParam,
                       @RequestParam(value = "pageNum", required = false) Integer pageNumParam,
                       @RequestParam(value = "pageSize", required = false) Integer pageSizeParam) {
        Long userId = resolveUserId(authorization, userIdParam);
        int pageNum = Math.max(pageNumParam == null ? 1 : pageNumParam, 1);
        int pageSize = Math.max(pageSizeParam == null ? 10 : pageSizeParam, 1);

        List<Order> allOrders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime));
        List<Map<String, Object>> rows = allOrders.stream().map(this::buildOrderRow).toList();
        int total = rows.size();

        int start = (pageNum - 1) * pageSize;
        List<Map<String, Object>> pageRows;
        if (start >= total) {
            pageRows = List.of();
        } else {
            int end = Math.min(total, start + pageSize);
            pageRows = rows.subList(start, end);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", pageRows);
        data.put("records", pageRows);
        data.put("total", total);
        data.put("count", total);
        return Result.success(data);
    }

    @GetMapping("/detail")
    public Result detail(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestParam("orderId") Long orderId) {
        Order order = queryOwnedOrder(authorization, orderId);
        return Result.success(buildOrderDetail(order));
    }

    @GetMapping("/query/{orderNo}")
    public Result queryByOrderNo(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @PathVariable("orderNo") String orderNo) {
        Order order = queryOwnedOrderByOrderNo(authorization, orderNo);
        return Result.success(buildOrderDetail(order));
    }

    @Transactional
    @PostMapping("/cancel")
    public Result cancel(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @RequestBody Map<String, Object> body) {
        Long orderId = DemoContextService.toLong(body.get("orderId"));
        if (orderId == null) {
            orderId = DemoContextService.toLong(body.get("id"));
        }
        if (orderId == null) {
            throw new BusinessException(400, "订单ID不能为空");
        }
        Order order = queryOwnedOrder(authorization, orderId);
        cancelOrder(order);
        return Result.success(buildOrderDetail(orderMapper.selectById(order.getId())));
    }

    @Transactional
    @PostMapping("/cancel/{orderNo}")
    public Result cancelByOrderNo(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @PathVariable("orderNo") String orderNo) {
        Order order = queryOwnedOrderByOrderNo(authorization, orderNo);
        cancelOrder(order);
        return Result.success(buildOrderDetail(orderMapper.selectById(order.getId())));
    }

    private void cancelOrder(Order order) {
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (Objects.equals(order.getPayStatus(), PAY_STATUS_CANCELED)) {
            return;
        }

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .set(Order::getPayStatus, PAY_STATUS_CANCELED));
        if (rows <= 0) {
            throw new BusinessException(500, "订单取消失败");
        }

        DemoContextService.OrderExtra extra = demoContextService.getOrderExtra(order.getId());
        List<Map<String, Object>> items = extra == null ? defaultOrderItems(order) : extra.getItems();
        for (Map<String, Object> item : items) {
            Long productId = DemoContextService.toLong(item.get("productId"));
            int quantity = Math.max(DemoContextService.toInt(item.get("quantity"), 1), 1);
            if (productId != null) {
                productMapper.update(null, new LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .setSql("stock = stock + " + quantity));
            }
        }
        demoContextService.updateOrderStatus(order.getId(), "CANCELED");
    }

    private Order queryOwnedOrder(String authorization, Long orderId) {
        Long userId = resolveUserId(authorization, null);
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(404, "订单不存在");
        }
        return order;
    }

    private Order queryOwnedOrderByOrderNo(String authorization, String orderNo) {
        Long userId = resolveUserId(authorization, null);
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo)
                .last("limit 1"));
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(404, "订单不存在");
        }
        return order;
    }

    private Map<String, Object> buildOrderRow(Order order) {
        DemoContextService.OrderExtra extra = demoContextService.getOrderExtra(order.getId());
        String status = extra != null && StringUtils.hasText(extra.getStatus())
                ? extra.getStatus()
                : mapOrderStatus(order.getPayStatus());
        String refundStatus = extra != null && StringUtils.hasText(extra.getRefundStatus())
                ? extra.getRefundStatus()
                : "NONE";

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("orderId", order.getId());
        row.put("orderNo", order.getOrderNo());
        row.put("status", status);
        row.put("refundStatus", refundStatus);
        row.put("amount", order.getAmount());
        row.put("totalAmount", order.getAmount());
        row.put("address", extra != null && StringUtils.hasText(extra.getAddress()) ? extra.getAddress() : order.getDeliveryAddress());
        row.put("remark", extra == null ? "" : extra.getRemark());
        row.put("createTime", DemoContextService.formatDateTime(order.getCreateTime()));
        row.put("expireTime", DemoContextService.formatDateTime(order.getExpireTime()));
        return row;
    }

    private Map<String, Object> buildOrderDetail(Order order) {
        Map<String, Object> row = buildOrderRow(order);
        DemoContextService.OrderExtra extra = demoContextService.getOrderExtra(order.getId());
        row.put("items", extra == null ? defaultOrderItems(order) : extra.getItems());
        return row;
    }

    private List<Map<String, Object>> defaultOrderItems(Order order) {
        Product product = order.getProductId() == null ? null : productMapper.selectById(order.getProductId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", order.getProductId());
        item.put("name", product == null ? "商品" + order.getProductId() : product.getName());
        item.put("price", product == null ? order.getAmount() : product.getPrice());
        item.put("quantity", 1);
        return List.of(item);
    }

    private List<Map<String, Object>> resolveOrderItems(Long userId, Map<String, Object> payload, Long productIdParam) {
        String source = stringValue(payload.get("source"));
        if ("cart".equalsIgnoreCase(source)) {
            List<Long> cartIds = toLongList(payload.get("cartItemIds"));
            List<DemoContextService.CartEntry> cartEntries = demoContextService.getCartEntriesByIds(userId, cartIds);
            List<Map<String, Object>> items = new ArrayList<>();
            for (DemoContextService.CartEntry cartEntry : cartEntries) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productId", cartEntry.getProductId());
                item.put("quantity", Math.max(cartEntry.getQuantity(), 1));
                items.add(item);
            }
            return items;
        }

        List<Map<String, Object>> requestItems = toMapList(payload.get("items"));
        if (!requestItems.isEmpty()) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> requestItem : requestItems) {
                Long productId = DemoContextService.toLong(requestItem.get("productId"));
                int quantity = Math.max(DemoContextService.toInt(requestItem.get("quantity"), 1), 1);
                if (productId == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productId", productId);
                item.put("quantity", quantity);
                result.add(item);
            }
            return result;
        }

        Long productId = productIdParam;
        if (productId == null) {
            productId = DemoContextService.toLong(payload.get("productId"));
        }
        if (productId == null) {
            Product latest = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, 1)
                    .orderByDesc(Product::getCreateTime)
                    .last("limit 1"));
            if (latest != null) {
                productId = latest.getId();
            }
        }
        if (productId == null) {
            return List.of();
        }
        int quantity = Math.max(DemoContextService.toInt(payload.get("quantity"), 1), 1);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", productId);
        item.put("quantity", quantity);
        return List.of(item);
    }

    private Long resolveUserId(String authorization, Long fallbackUserId) {
        if (StringUtils.hasText(authorization)) {
            return demoContextService.requireUserId(authorization);
        }
        if (fallbackUserId != null) {
            return fallbackUserId;
        }
        throw new BusinessException(401, "请先登录");
    }

    private static String resolveAddress(Map<String, Object> payload, String requestParamAddress) {
        if (StringUtils.hasText(requestParamAddress)) {
            return requestParamAddress;
        }
        String address = stringValue(payload.get("address"));
        if (!StringUtils.hasText(address)) {
            address = stringValue(payload.get("deliveryAddress"));
        }
        if (!StringUtils.hasText(address)) {
            address = "默认收货地址";
        }
        return address;
    }

    private static List<Long> toLongList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            Long parsed = DemoContextService.toLong(item);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String mapOrderStatus(Integer payStatus) {
        if (Objects.equals(payStatus, PAY_STATUS_PAYED)) {
            return "PAYED";
        }
        if (Objects.equals(payStatus, PAY_STATUS_CANCELED)) {
            return "CANCELED";
        }
        return "WAIT_PAY";
    }

    private static String buildOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
