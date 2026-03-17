-- 已有 product 表加商品描述字段（幂等，兼容 MySQL 8.0.24）
SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'product'
    AND COLUMN_NAME = 'description'
);

SET @ddl = IF(
  @col_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `description` VARCHAR(1024) DEFAULT NULL COMMENT ''商品描述'' AFTER `image_url`',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
