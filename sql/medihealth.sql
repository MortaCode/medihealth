-- ============================================================
-- MediHealth (微医健康) 医疗健康平台 - 核心建表脚本
-- Version: 1.0.0
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `medihealth`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `medihealth`;

-- ============================================================
-- 用户表：手机号作为系统唯一用户标识
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_user` (
    `id`          VARCHAR(32)  NOT NULL COMMENT '用户ID',
    `mobile`      VARCHAR(20)  NOT NULL COMMENT '手机号（唯一标识）',
    `nickname`    VARCHAR(128) DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(512) DEFAULT NULL COMMENT '头像',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 微信绑定表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_user_wechat` (
    `id`          VARCHAR(32)  NOT NULL COMMENT '主键',
    `user_id`     VARCHAR(32)  NOT NULL COMMENT '用户ID',
    `openid`      VARCHAR(128) NOT NULL COMMENT '微信OpenId',
    `unionid`     VARCHAR(128) DEFAULT NULL COMMENT '微信UnionId',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信绑定表';

-- ============================================================
-- 医疗商品表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_medical_product` (
    `id`                    VARCHAR(32)    NOT NULL COMMENT '商品ID',
    `name`                  VARCHAR(200)   NOT NULL COMMENT '商品名称',
    `description`           TEXT           DEFAULT NULL COMMENT '商品描述',
    `price`                 DECIMAL(10,2)  NOT NULL COMMENT '价格',
    `stock`                 INT            NOT NULL DEFAULT 0 COMMENT '库存',
    `image`                 VARCHAR(512)   DEFAULT NULL COMMENT '主图',
    `images`                TEXT           DEFAULT NULL COMMENT '轮播图（JSON数组）',
    `category`              VARCHAR(64)    DEFAULT NULL COMMENT '分类：药品/保健品/体检套餐/医疗服务',
    `status`                TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
    `prescription_required` TINYINT        NOT NULL DEFAULT 0 COMMENT '是否需要处方：0-否 1-是',
    `sales`                 INT            NOT NULL DEFAULT 0 COMMENT '销量',
    `create_time`           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医疗商品表';

-- ============================================================
-- 购物车表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_cart_item` (
    `id`          VARCHAR(32) NOT NULL COMMENT '主键',
    `user_id`     VARCHAR(32) NOT NULL COMMENT '用户ID',
    `product_id`  VARCHAR(32) NOT NULL COMMENT '商品ID',
    `quantity`    INT         NOT NULL DEFAULT 1 COMMENT '数量',
    `selected`    TINYINT     NOT NULL DEFAULT 1 COMMENT '是否选中：0-未选 1-选中',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

-- ============================================================
-- 订单主表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_order` (
    `id`                VARCHAR(32)    NOT NULL COMMENT '主键',
    `order_id`          VARCHAR(32)    NOT NULL COMMENT '订单号（业务主键）',
    `user_id`           VARCHAR(32)    NOT NULL COMMENT '用户ID',
    `amount`            DECIMAL(10,2)  NOT NULL COMMENT '订单金额',
    `status`            TINYINT        NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付 1-支付中 2-已支付 3-已取消 4-退款中 5-已退款 6-已关闭',
    `version`           INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `prescription_flag` TINYINT        NOT NULL DEFAULT 0 COMMENT '是否含处方药：0-否 1-是',
    `pay_time`          DATETIME       DEFAULT NULL COMMENT '支付成功时间',
    `expire_time`       DATETIME       NOT NULL COMMENT '订单过期时间（30分钟）',
    `del_flag`          TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    `create_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- ============================================================
-- 订单商品快照表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_order_item` (
    `id`                    VARCHAR(32)    NOT NULL COMMENT '主键',
    `order_id`              VARCHAR(32)    NOT NULL COMMENT '订单ID',
    `product_id`            VARCHAR(32)    NOT NULL COMMENT '商品ID',
    `product_name`          VARCHAR(200)   NOT NULL COMMENT '商品名称（快照）',
    `product_image`         VARCHAR(512)   DEFAULT NULL COMMENT '商品图片（快照）',
    `price`                 DECIMAL(10,2)  NOT NULL COMMENT '下单时单价',
    `quantity`              INT            NOT NULL COMMENT '数量',
    `amount`                DECIMAL(10,2)  NOT NULL COMMENT '小计',
    `prescription_required` TINYINT        NOT NULL DEFAULT 0 COMMENT '是否需要处方',
    `create_time`           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品快照表';

-- ============================================================
-- 支付记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_payment_record` (
    `id`               VARCHAR(32)  NOT NULL COMMENT '主键',
    `payment_no`       VARCHAR(32)  NOT NULL COMMENT '支付流水号',
    `order_id`         VARCHAR(32)  NOT NULL COMMENT '订单号',
    `user_id`          VARCHAR(32)  NOT NULL COMMENT '用户ID',
    `amount`           DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '支付状态：0-处理中 1-成功 2-失败 3-退款中 4-已退款',
    `channel`          VARCHAR(20)  NOT NULL COMMENT '支付渠道：WECHAT/ALIPAY',
    `channel_order_no` VARCHAR(64)  DEFAULT NULL COMMENT '第三方支付订单号',
    `error_code`       VARCHAR(32)  DEFAULT NULL COMMENT '错误码',
    `error_msg`        VARCHAR(256) DEFAULT NULL COMMENT '错误信息',
    `notify_time`      DATETIME     DEFAULT NULL COMMENT '回调通知时间',
    `del_flag`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- ============================================================
-- 幂等控制表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_idempotent_record` (
    `id`             VARCHAR(32)  NOT NULL COMMENT '主键',
    `idempotent_key` VARCHAR(128) NOT NULL COMMENT '幂等键',
    `business_type`  VARCHAR(32)  NOT NULL COMMENT '业务类型',
    `business_id`    VARCHAR(64)  NOT NULL COMMENT '业务ID',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态',
    `result_data`    TEXT         DEFAULT NULL COMMENT '处理结果JSON',
    `expire_time`    DATETIME     NOT NULL COMMENT '过期时间',
    `del_flag`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
    KEY `idx_business_id` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等控制表';

-- ============================================================
-- 义诊名额库存表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_quota` (
    `id`              VARCHAR(32)  NOT NULL COMMENT '主键ID',
    `quota_id`        VARCHAR(32)  NOT NULL COMMENT '名额ID',
    `quota_name`      VARCHAR(128) NOT NULL COMMENT '名额名称（如：专家义诊号）',
    `total_quota`     INT          NOT NULL DEFAULT 0 COMMENT '总名额数',
    `remaining_quota` INT          NOT NULL DEFAULT 0 COMMENT '剩余名额',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_quota_id` (`quota_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='义诊/体检名额库存表';

-- ============================================================
-- 健康资讯文章表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_health_article` (
    `id`          VARCHAR(32)   NOT NULL PRIMARY KEY,
    `user_id`     VARCHAR(32)   NOT NULL,
    `title`       VARCHAR(512)  DEFAULT NULL COMMENT '标题',
    `cover_img`   VARCHAR(1024) DEFAULT NULL COMMENT '封面图',
    `content`     TEXT          NOT NULL COMMENT '文章内容',
    `author_type` VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM' COMMENT '作者类型：DOCTOR/USER/SYSTEM',
    `author_name` VARCHAR(128)  DEFAULT NULL COMMENT '作者名称',
    `category`    VARCHAR(64)   DEFAULT NULL COMMENT '分类：疾病知识/用药指南/养生保健/医学科普/饮食健康/运动健身',
    `like_count`  INT           DEFAULT 0 NOT NULL COMMENT '点赞数',
    `view_count`  INT           DEFAULT 0 NOT NULL COMMENT '阅读数',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_category` (`category`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健康资讯文章表';

-- ============================================================
-- 点赞记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_like_record` (
    `id`          VARCHAR(32) NOT NULL PRIMARY KEY,
    `user_id`     VARCHAR(32) NOT NULL,
    `article_id`  VARCHAR(32) NOT NULL,
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    UNIQUE KEY `idx_user_article` (`user_id`, `article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录表';

-- ============================================================
-- 对话会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_session` (
    `id`            VARCHAR(32)  NOT NULL COMMENT '会话ID',
    `user_id`       VARCHAR(64)  NOT NULL COMMENT '用户ID',
    `title`         VARCHAR(200) DEFAULT NULL COMMENT '会话标题',
    `model_name`    VARCHAR(50)  NOT NULL COMMENT '使用的模型名称',
    `message_count` INT          DEFAULT 0 COMMENT '消息总数',
    `del_flag`      TINYINT      DEFAULT 0 COMMENT '删除状态',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- ============================================================
-- 对话消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_message` (
    `id`           VARCHAR(32)  NOT NULL COMMENT '消息ID',
    `session_id`   VARCHAR(32)  NOT NULL COMMENT '会话ID',
    `role`         VARCHAR(20)  NOT NULL COMMENT '角色：user/assistant/system',
    `content`      TEXT         NOT NULL COMMENT '消息内容',
    `message_type` VARCHAR(20)  DEFAULT 'text' COMMENT '消息类型',
    `token_count`  INT          DEFAULT 0 COMMENT 'token数量',
    `del_flag`     TINYINT      DEFAULT 0 COMMENT '删除状态',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';

-- ============================================================
-- 会话记忆快照表
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_snapshot` (
    `id`            VARCHAR(32)  NOT NULL COMMENT '快照ID',
    `session_id`    VARCHAR(32)  NOT NULL COMMENT '会话ID',
    `snapshot_data` LONGBLOB     NOT NULL COMMENT '序列化的记忆数据',
    `message_count` INT          NOT NULL COMMENT '快照时的消息数量',
    `checksum`      VARCHAR(64)  DEFAULT NULL COMMENT '数据校验和',
    `del_flag`      TINYINT      DEFAULT 0 COMMENT '删除状态',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话记忆快照表';
