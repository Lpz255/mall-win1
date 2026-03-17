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
 * 秒杀订单实体。
 */
@Data
@TableName("seckill_order")
public class SeckillOrder implements Serializable {

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

    /** 秒杀成交价。 */
    @TableField("seckill_price")
    private BigDecimal seckillPrice;

    /** 订单状态（1：已创建）。 */
    @TableField("status")
    private Integer status;

    /** 创建时间。 */
    @TableField("create_time")
    private LocalDateTime createTime;
}