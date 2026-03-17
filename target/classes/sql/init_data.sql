-- 演示数据初始化脚本（可重复执行）
-- 依赖：product / seckill_product / user_account 表已创建

INSERT INTO user_account (id, phone, password, name, user_level, status, register_time, last_login_time)
SELECT 10001, '13800138000', '123456', '演示用户8000', '普通会员', 'enabled', DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 3 HOUR)
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE phone = '13800138000');

INSERT INTO product (id, name, price, stock, category_id, image_url, status, create_time)
SELECT 1001, '旗舰游戏手机', 3299.00, 120, 1, 'https://via.placeholder.com/640x640?text=Phone+1001', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 1001);

INSERT INTO product (id, name, price, stock, category_id, image_url, status, create_time)
SELECT 1002, '蓝牙降噪耳机', 399.00, 300, 1, 'https://via.placeholder.com/640x640?text=Headset+1002', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 1002);

INSERT INTO product (id, name, price, stock, category_id, image_url, status, create_time)
SELECT 1003, '轻薄办公笔记本', 4999.00, 80, 1, 'https://via.placeholder.com/640x640?text=Laptop+1003', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 1003);

INSERT INTO product (id, name, price, stock, category_id, image_url, status, create_time)
SELECT 1004, '智能空气炸锅', 269.00, 200, 2, 'https://via.placeholder.com/640x640?text=Cooker+1004', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 1004);

INSERT INTO product (id, name, price, stock, category_id, image_url, status, create_time)
SELECT 1005, '休闲双肩背包', 159.00, 260, 3, 'https://via.placeholder.com/640x640?text=Bag+1005', 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 1005);

INSERT INTO product_detail (product_id, detail_content)
SELECT 1001, '旗舰游戏手机，搭载高性能处理器与高刷屏幕，适合重度游戏和影音场景。'
WHERE NOT EXISTS (SELECT 1 FROM product_detail WHERE product_id = 1001);

INSERT INTO product_detail (product_id, detail_content)
SELECT 1002, '蓝牙降噪耳机，支持主动降噪与长续航，通勤办公均可使用。'
WHERE NOT EXISTS (SELECT 1 FROM product_detail WHERE product_id = 1002);

INSERT INTO product_detail (product_id, detail_content)
SELECT 1003, '轻薄办公笔记本，兼顾性能与便携，满足日常办公和轻度创作需求。'
WHERE NOT EXISTS (SELECT 1 FROM product_detail WHERE product_id = 1003);

INSERT INTO seckill_product (id, product_id, seckill_price, seckill_stock, start_time, end_time, status)
SELECT 2001, 1001, 2999.00, 60, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 23 HOUR), 1
WHERE NOT EXISTS (SELECT 1 FROM seckill_product WHERE id = 2001);

INSERT INTO seckill_product (id, product_id, seckill_price, seckill_stock, start_time, end_time, status)
SELECT 2002, 1004, 199.00, 80, DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_ADD(NOW(), INTERVAL 10 HOUR), 1
WHERE NOT EXISTS (SELECT 1 FROM seckill_product WHERE id = 2002);
