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
 * 秒杀商品实体。
 */
@Data
@TableName("seckill_product")
public class SeckillProduct implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 商品ID。 */
    @TableField("product_id")
    private Long productId;

    /** 秒杀价格。 */
    @TableField("seckill_price")
    private BigDecimal seckillPrice;

    /** 秒杀库存。 */
    @TableField("seckill_stock")
    private Integer seckillStock;

    /** 秒杀开始时间。 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 秒杀结束时间。 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 状态（1：启用，0：停用）。 */
    @TableField("status")
    private Integer status;
}