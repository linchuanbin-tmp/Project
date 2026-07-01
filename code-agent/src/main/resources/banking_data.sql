-- ============================================================
-- Code Agent 银行场景模拟数据
-- 基于 agent_platform 库新增银行业务表
-- ============================================================

USE agent_platform;

-- ----------------------------
-- 1. 客户信息表
-- ----------------------------
DROP TABLE IF EXISTS `bank_customer`;
CREATE TABLE `bank_customer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `customer_no` VARCHAR(20) NOT NULL COMMENT '客户编号',
    `name` VARCHAR(50) NOT NULL COMMENT '客户姓名',
    `id_card` VARCHAR(18) DEFAULT NULL COMMENT '身份证号',
    `phone` VARCHAR(15) DEFAULT NULL COMMENT '手机号',
    `risk_level` VARCHAR(10) DEFAULT 'LOW' COMMENT '风险等级: LOW/MEDIUM/HIGH',
    `status` INT DEFAULT 1 COMMENT '1正常 0冻结',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` INT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_no` (`customer_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行客户表';

-- ----------------------------
-- 2. 账户表
-- ----------------------------
DROP TABLE IF EXISTS `bank_account`;
CREATE TABLE `bank_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `account_no` VARCHAR(30) NOT NULL COMMENT '账号',
    `customer_id` BIGINT NOT NULL COMMENT '客户ID',
    `account_type` VARCHAR(20) DEFAULT 'SAVINGS' COMMENT '账户类型: SAVINGS/CHECKING/FIXED',
    `balance` DECIMAL(18,2) DEFAULT 0.00 COMMENT '余额',
    `currency` VARCHAR(5) DEFAULT 'CNY' COMMENT '币种',
    `open_date` DATE DEFAULT NULL COMMENT '开户日期',
    `status` INT DEFAULT 1 COMMENT '1正常 0销户',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `deleted` INT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_no` (`account_no`),
    KEY `idx_customer_id` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行账户表';

-- ----------------------------
-- 3. 交易流水表
-- ----------------------------
DROP TABLE IF EXISTS `bank_transaction`;
CREATE TABLE `bank_transaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `txn_no` VARCHAR(40) NOT NULL COMMENT '交易流水号',
    `account_id` BIGINT NOT NULL COMMENT '账户ID',
    `txn_type` VARCHAR(20) NOT NULL COMMENT '交易类型: DEPOSIT/WITHDRAW/TRANSFER_IN/TRANSFER_OUT',
    `amount` DECIMAL(18,2) NOT NULL COMMENT '金额',
    `balance_after` DECIMAL(18,2) DEFAULT 0.00 COMMENT '交易后余额',
    `counterparty_account` VARCHAR(30) DEFAULT NULL COMMENT '对方账号',
    `remark` VARCHAR(100) DEFAULT NULL COMMENT '备注',
    `txn_time` DATETIME NOT NULL COMMENT '交易时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_txn_time` (`txn_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易流水表';

-- ============================================================
-- 模拟数据
-- ============================================================

-- 客户数据
INSERT INTO `bank_customer` VALUES
(1, 'C001', '张三', '110101199001011234', '13800001111', 'LOW',  1, NOW(), NOW(), 0),
(2, 'C002', '李四', '110101199202023456', '13800002222', 'LOW',  1, NOW(), NOW(), 0),
(3, 'C003', '王五', '110101199303035678', '13800003333', 'MEDIUM', 1, NOW(), NOW(), 0),
(4, 'C004', '赵六', '110101199404048901', '13800004444', 'HIGH', 1, NOW(), NOW(), 0),
(5, 'C005', '孙七', '110101199505051234', '13800005555', 'LOW',  1, NOW(), NOW(), 0);

-- 账户数据
INSERT INTO `bank_account` VALUES
(1, '6222021001000001', 1, 'SAVINGS',   150000.00, 'CNY', '2024-01-15', 1, NOW(), 0),
(2, '6222021001000002', 1, 'CHECKING',   35000.00, 'CNY', '2024-03-20', 1, NOW(), 0),
(3, '6222021001000003', 2, 'SAVINGS',    85000.00, 'CNY', '2024-02-10', 1, NOW(), 0),
(4, '6222021001000004', 3, 'SAVINGS',    22000.00, 'CNY', '2024-05-05', 1, NOW(), 0),
(5, '6222021001000005', 4, 'SAVINGS',  1200000.00, 'CNY', '2023-08-01', 1, NOW(), 0),
(6, '6222021001000006', 5, 'CHECKING',   18000.00, 'CNY', '2024-06-01', 1, NOW(), 0),
(7, '6222021001000007', 1, 'FIXED',     500000.00, 'CNY', '2024-01-15', 1, NOW(), 0);

-- 交易流水数据
INSERT INTO `bank_transaction` VALUES
(1,  'TXN20260601001', 1, 'DEPOSIT',      50000.00, 150000.00, NULL,            '工资入账',      '2026-06-01 09:00:00', NOW()),
(2,  'TXN20260602001', 1, 'WITHDRAW',     10000.00, 140000.00, NULL,            'ATM取款',       '2026-06-02 14:30:00', NOW()),
(3,  'TXN20260603001', 1, 'TRANSFER_OUT',  5000.00, 135000.00, '6222021001000003', '转账给李四',   '2026-06-03 10:15:00', NOW()),
(4,  'TXN20260603002', 3, 'TRANSFER_IN',   5000.00,  90000.00, '6222021001000001', '张三转入',     '2026-06-03 10:15:00', NOW()),
(5,  'TXN20260605001', 2, 'DEPOSIT',      20000.00,  35000.00, NULL,            '存款',          '2026-06-05 16:00:00', NOW()),
(6,  'TXN20260608001', 4, 'WITHDRAW',      3000.00,  19000.00, NULL,            '柜台取款',      '2026-06-08 11:20:00', NOW()),
(7,  'TXN20260610001', 5, 'TRANSFER_OUT', 20000.00, 1180000.00,'6222021001000006','转账给孙七',   '2026-06-10 09:45:00', NOW()),
(8,  'TXN20260610002', 6, 'TRANSFER_IN',  20000.00,  38000.00, '6222021001000005','赵六转入',     '2026-06-10 09:45:00', NOW()),
(9,  'TXN20260612001', 1, 'DEPOSIT',      15000.00, 135000.00, NULL,            '理财收益',      '2026-06-12 08:00:00', NOW()),
(10, 'TXN20260615001', 3, 'WITHDRAW',      8000.00,  77000.00, NULL,            '消费支出',      '2026-06-15 18:30:00', NOW());
