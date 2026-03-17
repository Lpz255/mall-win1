package com.example.demo.entity;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀下单消息体。
 */
@Data
public class SeckillOrderMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号。 */
    private String orderNo;

    /** 用户ID。 */
    private Long userId;

    /** 商品ID。 */
    private Long productId;

    /** 秒杀价格。 */
    private BigDecimal seckillPrice;

    /** 消息创建时间。 */
    private LocalDateTime createTime;
}