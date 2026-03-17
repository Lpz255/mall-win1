package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.demo.config.OrderProperties;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.mapper.OrderMapper;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.utils.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * 订单服务。
 * <p>
 * 提供能力：
 * 1. 创建订单。
 * 2. 查询订单。
 * 3. 取消订单。
 * 4. 订单超时自动取消。
 * </p>
 */
@Service
public class OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);

    private static final int PRODUCT_STATUS_ON_SALE = 1;
    private static final int PAY_STATUS_UNPAID = 0;
    private static final int PAY_STATUS_PAID = 1;
    private static final int PAY_STATUS_CANCELED = 2;

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final OrderDelayProducer orderDelayProducer;
    private final OrderProperties orderProperties;

    public OrderService(OrderMapper orderMapper,
                        ProductMapper productMapper,
                        OrderDelayProducer orderDelayProducer,
                        OrderProperties orderProperties) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.orderDelayProducer = orderDelayProducer;
        this.orderProperties = orderProperties;
    }

    /**
     * 创建订单。
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @param deliveryAddress 收货地址
     * @return 订单号
     */
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(Long userId, Long productId, String deliveryAddress) {
        validateCreateParam(userId, productId, deliveryAddress);

        Product product = productMapper.selectById(productId);
        if (product == null || !Objects.equals(product.getStatus(), PRODUCT_STATUS_ON_SALE)) {
            throw new BusinessException(4042, "商品不存在或已下架");
        }

        int stockDeductRows = productMapper.update(
                null,
                new UpdateWrapper<Product>()
                        .eq("id", productId)
                        .eq("status", PRODUCT_STATUS_ON_SALE)
                        .gt("stock", 0)
                        .setSql("stock = stock - 1")
        );
        if (stockDeductRows <= 0) {
            throw new BusinessException(4008, "商品库存不足");
        }

        LocalDateTime now = LocalDateTime.now();
        String orderNo = buildOrderNo(userId, productId);

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setAmount(product.getPrice());
        order.setPayStatus(PAY_STATUS_UNPAID);
        order.setDeliveryAddress(deliveryAddress);
        order.setCreateTime(now);
        order.setExpireTime(now.plusMinutes(orderProperties.getExpireMinutes()));

        int insertRows = orderMapper.insert(order);
        if (insertRows <= 0) {
            throw new BusinessException(5009, "订单创建失败");
        }

        try {
            // 发送延迟消息，达到延迟时间后消费端自动判断是否需要取消订单。
            orderDelayProducer.sendOrderDelayMessage(orderNo, orderProperties.getDelayCancelMillis());
        } catch (AmqpException exception) {
            throw new BusinessException(5010, "订单超时消息发送失败");
        }

        return orderNo;
    }

    /**
     * 查询订单。
     *
     * @param orderNo 订单号
     * @return 订单详情
     */
    public Order queryOrder(String orderNo) {
        Order order = findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(4043, "订单不存在");
        }
        return order;
    }

    /**
     * 取消订单。
     *
     * @param orderNo 订单号
     * @return 取消后的订单
     */
    @Transactional(rollbackFor = Exception.class)
    public Order cancelOrder(String orderNo) {
        Order order = queryOrder(orderNo);

        if (Objects.equals(order.getPayStatus(), PAY_STATUS_PAID)) {
            throw new BusinessException(4011, "订单已支付，无法取消");
        }
        if (Objects.equals(order.getPayStatus(), PAY_STATUS_CANCELED)) {
            return order;
        }

        int updateRows = orderMapper.update(
                null,
                new UpdateWrapper<Order>()
                        .eq("id", order.getId())
                        .eq("pay_status", PAY_STATUS_UNPAID)
                        .set("pay_status", PAY_STATUS_CANCELED)
        );
        if (updateRows <= 0) {
            throw new BusinessException(5011, "订单状态已变更，请刷新后重试");
        }

        restoreProductStock(order.getProductId());
        order.setPayStatus(PAY_STATUS_CANCELED);
        return order;
    }

    /**
     * 处理订单超时取消。
     *
     * @param orderNo 订单号
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleTimeoutCancel(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return;
        }

        Order order = findByOrderNo(orderNo);
        if (order == null) {
            return;
        }

        if (!Objects.equals(order.getPayStatus(), PAY_STATUS_UNPAID)) {
            return;
        }

        // 只有到达或超过过期时间才自动取消，避免提前取消订单。
        if (order.getExpireTime() != null && LocalDateTime.now().isBefore(order.getExpireTime())) {
            return;
        }

        int updateRows = orderMapper.update(
                null,
                new UpdateWrapper<Order>()
                        .eq("id", order.getId())
                        .eq("pay_status", PAY_STATUS_UNPAID)
                        .set("pay_status", PAY_STATUS_CANCELED)
        );

        if (updateRows > 0) {
            restoreProductStock(order.getProductId());
            LOGGER.info("订单超时已取消，orderNo={}", orderNo);
        }
    }

    /**
     * 根据订单号查询订单。
     *
     * @param orderNo 订单号
     * @return 订单对象
     */
    private Order findByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BusinessException(4005, "订单号不能为空");
        }
        return orderMapper.selectOne(
                new QueryWrapper<Order>()
                        .eq("order_no", orderNo)
                        .last("limit 1")
        );
    }

    /**
     * 恢复商品库存。
     *
     * @param productId 商品ID
     */
    private void restoreProductStock(Long productId) {
        int restoreRows = productMapper.update(
                null,
                new UpdateWrapper<Product>()
                        .eq("id", productId)
                        .setSql("stock = stock + 1")
        );
        if (restoreRows <= 0) {
            throw new BusinessException(5012, "商品库存恢复失败");
        }
    }

    /**
     * 入参校验。
     */
    private void validateCreateParam(Long userId, Long productId, String deliveryAddress) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(4004, "用户ID不合法");
        }
        if (productId == null || productId <= 0) {
            throw new BusinessException(4004, "商品ID不合法");
        }
        if (!StringUtils.hasText(deliveryAddress)) {
            throw new BusinessException(4006, "收货地址不能为空");
        }
    }

    /**
     * 生成订单号。
     */
    private String buildOrderNo(Long userId, Long productId) {
        long epochMilli = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "ORD" + epochMilli + userId + productId + UUID.randomUUID().toString().substring(0, 6);
    }
}