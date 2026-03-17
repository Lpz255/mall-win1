-- 商品详情表（MySQL 8.0.24）
CREATE TABLE IF NOT EXISTS `product_detail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `detail_content` TEXT COMMENT '详情内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='商品详情表';

-- 将已有 product.description 回填到详情表（幂等）
INSERT INTO product_detail (product_id, detail_content)
SELECT p.id, p.description
FROM product p
WHERE p.description IS NOT NULL
  AND TRIM(p.description) <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM product_detail d
    WHERE d.product_id = p.id
  );
