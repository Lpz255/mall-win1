package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体。
 */
@Data
@TableName("`order`")
public class Order implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单号。 */
    @TableField("order_no")
    private String orderNo;

    /** 用户ID。 */
    @TableField("user_id")
    private Long userId;

    /** 商品ID。 */
    @TableField("product_id")
    private Long productId;

    /** 订单金额。 */
    @TableField("amount")
    private BigDecimal amount;

    /** 支付状态（0待支付，1已支付，2已取消）。 */
    @TableField("pay_status")
    private Integer payStatus;

    /** 收货地址。 */
    @TableField("delivery_address")
    private String deliveryAddress;

    /** 创建时间。 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 过期时间。 */
    @TableField("expire_time")
    private LocalDateTime expireTime;
}