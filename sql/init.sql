-- =============================================================
-- 校淘 CampusMart 初始化脚本
-- 执行方式：mysql -uroot -p < sql/init.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS `campusmart`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `campusmart`;

-- -------------------------------------------------------------
-- 用户表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码摘要（SHA-256 加盐）',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0 禁用 1 正常',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';

-- -------------------------------------------------------------
-- 商品表（商品服务）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品 ID',
    `title`       VARCHAR(200)  NOT NULL COMMENT '标题',
    `description` VARCHAR(2000) DEFAULT NULL COMMENT '描述',
    `price`       DECIMAL(10, 2) NOT NULL COMMENT '价格',
    `stock`       INT           NOT NULL DEFAULT 0 COMMENT '库存',
    `version`     INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（普通下单用）',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '0 下架 1 在售',
    `seller_id`   BIGINT        NOT NULL COMMENT '卖家 ID',
    `cover_image` VARCHAR(255)  DEFAULT NULL COMMENT '封面图',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_seller` (`seller_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '商品表';

-- -------------------------------------------------------------
-- 秒杀活动表（交易服务，同时承担秒杀库存持久化）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `seckill_activity`;
CREATE TABLE `seckill_activity` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '活动 ID',
    `product_id`    BIGINT         NOT NULL COMMENT '关联商品 ID',
    `seckill_price` DECIMAL(10, 2) NOT NULL COMMENT '秒杀价',
    `stock`         INT            NOT NULL COMMENT '秒杀库存',
    `version`       INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `start_time`    DATETIME       NOT NULL COMMENT '开始时间',
    `end_time`      DATETIME       NOT NULL COMMENT '结束时间',
    `status`        TINYINT        NOT NULL DEFAULT 0 COMMENT '0 未开始 1 进行中 2 已结束',
    `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product` (`product_id`),
    KEY `idx_time` (`start_time`, `end_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '秒杀活动表';

-- -------------------------------------------------------------
-- 订单表（交易服务）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单 ID',
    `order_no`    VARCHAR(64)    NOT NULL COMMENT '业务订单号，MQ 幂等键',
    `user_id`     BIGINT         NOT NULL COMMENT '买家 ID',
    `product_id`  BIGINT         NOT NULL COMMENT '商品 ID',
    `activity_id` BIGINT         DEFAULT NULL COMMENT '秒杀活动 ID，普通订单为 NULL',
    `amount`      DECIMAL(10, 2) NOT NULL COMMENT '金额',
    `status`      TINYINT        NOT NULL DEFAULT 0 COMMENT '0 处理中 1 成功 2 失败',
    `fail_reason` VARCHAR(255)   DEFAULT NULL COMMENT '失败原因',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user` (`user_id`),
    KEY `idx_activity` (`activity_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '订单表';

-- -------------------------------------------------------------
-- 初始化演示数据
-- 密码统一为 123456（SHA-256('123456' + 固定盐) 的摘要，与 PasswordUtil 保持一致）
-- -------------------------------------------------------------
INSERT INTO `user` (`username`, `password`, `nickname`, `status`)
VALUES ('test01', '9332d901163590c3d9a4c676d3719c19c2db0763788b724b4e57c12d36adfc59', '测试用户01', 1),
       ('test02', '9332d901163590c3d9a4c676d3719c19c2db0763788b724b4e57c12d36adfc59', '测试用户02', 1);

INSERT INTO `product` (`title`, `description`, `price`, `stock`, `version`, `status`, `seller_id`)
VALUES ('高等数学（第七版）上册', '大一教材，九成新，无笔记', 25.00, 10, 0, 1, 1),
       ('罗技 M330 无线鼠标', '静音款，用了一学期，功能正常', 45.00, 5, 0, 1, 1),
       ('宿舍小台灯', '可调亮度，附赠充电线', 18.50, 20, 0, 1, 2);

-- 一个正在进行中的秒杀活动，库存 100，方便直接压测
INSERT INTO `seckill_activity` (`product_id`, `seckill_price`, `stock`, `version`, `start_time`, `end_time`, `status`)
VALUES (1, 9.90, 100, 0, DATE_SUB(NOW(), INTERVAL 1 MINUTE), DATE_ADD(NOW(), INTERVAL 7 DAY), 1);
