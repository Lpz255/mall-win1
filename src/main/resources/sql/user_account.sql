-- 用户账号表（MySQL 8.0.24）
CREATE TABLE IF NOT EXISTS `user_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
  `password` VARCHAR(128) NOT NULL COMMENT '登录密码',
  `name` VARCHAR(64) NOT NULL COMMENT '用户昵称',
  `user_level` VARCHAR(32) NOT NULL DEFAULT '普通会员' COMMENT '用户等级',
  `status` VARCHAR(16) NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled/disabled',
  `register_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最近登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_status_register_time` (`status`, `register_time`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户账号表';
