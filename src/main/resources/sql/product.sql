-- 商品表（MySQL 8.0.24）
-- 字符集：utf8mb4，排序规则：utf8mb4_unicode_ci
CREATE TABLE IF NOT EXISTS `product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `name` VARCHAR(128) NOT NULL COMMENT '商品名称',
  `price` DECIMAL(10,2) NOT NULL COMMENT '商品价格',
  `stock` INT NOT NULL DEFAULT 0 COMMENT '商品库存',
  `category_id` BIGINT NOT NULL COMMENT '分类ID',
  `image_url` VARCHAR(512) DEFAULT NULL COMMENT '图片地址',
  `description` VARCHAR(1024) DEFAULT NULL COMMENT '商品描述',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1上架，0下架',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='商品表';
