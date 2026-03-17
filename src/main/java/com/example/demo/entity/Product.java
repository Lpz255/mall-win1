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
 * 商品实体。
 */
@Data
@TableName("product")
public class Product implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 商品ID。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商品名称。
     */
    @TableField("name")
    private String name;

    /**
     * 商品价格。
     */
    @TableField("price")
    private BigDecimal price;

    /**
     * 商品库存。
     */
    @TableField("stock")
    private Integer stock;

    /**
     * 商品分类ID。
     */
    @TableField("category_id")
    private Long categoryId;

    /**
     * 商品图片地址。
     */
    @TableField("image_url")
    private String imageUrl;

    /**
     * 商品描述。
     */
    @TableField("description")
    private String description;

    /**
     * 商品状态（1：上架，0：下架）。
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间。
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
