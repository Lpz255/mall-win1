-- 秒杀商品表（MySQL 8.0.24）
CREATE TABLE IF NOT EXISTS `seckill_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
  `seckill_stock` INT NOT NULL DEFAULT 0 COMMENT '秒杀库存',
  `start_time` DATETIME NOT NULL COMMENT '秒杀开始时间',
  `end_time` DATETIME NOT NULL COMMENT '秒杀结束时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`),
  KEY `idx_status_time` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='秒杀商品表';