-- ============================================================
-- Bank HR & Loan Tables (预留扩展)
-- ⚠️ 当前 Text-to-SQL 模型仅覆盖 3 张核心表：
--    bank_customer, bank_account, bank_transaction
--    以下 3 张表为未来扩展预留，当前不在模型覆盖范围
-- ============================================================
USE agent_platform;

-- ----------------------------
-- Department table
-- ----------------------------
CREATE TABLE IF NOT EXISTS bank_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dept_name VARCHAR(50) NOT NULL COMMENT 'Department Name',
    manager_id BIGINT DEFAULT NULL COMMENT 'Manager Employee ID',
    floor INT DEFAULT NULL COMMENT 'Office Floor',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bank Department';

INSERT IGNORE INTO bank_department VALUES
(1, 'Retail Banking',     1, 1, NOW(), 0),
(2, 'Corporate Banking',  2, 2, NOW(), 0),
(3, 'Risk Management',    1, 3, NOW(), 0),
(4, 'IT Department',      4, 4, NOW(), 0),
(5, 'Human Resources',    5, 3, NOW(), 0);

-- ----------------------------
-- Employee table
-- ----------------------------
CREATE TABLE IF NOT EXISTS bank_employee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_no VARCHAR(20) NOT NULL UNIQUE COMMENT 'Employee No',
    name VARCHAR(50) NOT NULL COMMENT 'Name',
    dept_id BIGINT NOT NULL COMMENT 'Department ID',
    position VARCHAR(50) COMMENT 'Position',
    salary DECIMAL(12,2) COMMENT 'Monthly Salary',
    hire_date DATE COMMENT 'Hire Date',
    phone VARCHAR(15),
    status INT DEFAULT 1 COMMENT '1 Active 0 Inactive',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bank Employee';

INSERT IGNORE INTO bank_employee VALUES
(1,  'E001', 'Alice Wang',    1, 'Branch Manager',      25000.00, '2020-03-15', '13900001111', 1, NOW(), 0),
(2,  'E002', 'Bob Chen',      2, 'Relationship Manager', 20000.00, '2020-06-01', '13900002222', 1, NOW(), 0),
(3,  'E003', 'Carol Liu',     1, 'Teller',               8000.00, '2021-01-10', '13900003333', 1, NOW(), 0),
(4,  'E004', 'David Zhang',   4, 'Software Engineer',    18000.00, '2019-09-01', '13900004444', 1, NOW(), 0),
(5,  'E005', 'Eva Li',        5, 'HR Director',          22000.00, '2018-07-20', '13900005555', 1, NOW(), 0),
(6,  'E006', 'Frank Wu',      3, 'Risk Analyst',         15000.00, '2021-06-15', '13900006666', 1, NOW(), 0),
(7,  'E007', 'Grace Zhao',    4, 'System Admin',         16000.00, '2020-11-01', '13900007777', 1, NOW(), 0),
(8,  'E008', 'Henry Sun',     1, 'Customer Service',      7500.00, '2022-03-01', '13900008888', 1, NOW(), 0),
(9,  'E009', 'Iris Zhou',     2, 'Credit Analyst',       17000.00, '2019-04-15', '13900009999', 1, NOW(), 0),
(10, 'E010', 'Jack Ma',       3, 'Compliance Officer',   19000.00, '2018-01-10', '13900001000', 1, NOW(), 0);

-- ----------------------------
-- Loan table
-- ----------------------------
CREATE TABLE IF NOT EXISTS bank_loan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_no VARCHAR(30) NOT NULL UNIQUE COMMENT 'Loan No',
    customer_id BIGINT NOT NULL COMMENT 'Customer ID',
    emp_id BIGINT COMMENT 'Approved By Employee ID',
    loan_type VARCHAR(30) COMMENT 'HOUSING/AUTO/PERSONAL/BUSINESS',
    amount DECIMAL(14,2) NOT NULL COMMENT 'Loan Amount',
    interest_rate DECIMAL(5,2) COMMENT 'Annual Interest Rate %',
    term_months INT COMMENT 'Term in Months',
    start_date DATE COMMENT 'Start Date',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAID_OFF/DEFAULTED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bank Loan';

INSERT IGNORE INTO bank_loan VALUES
(1,  'LN20240001', 1, 1, 'HOUSING',   800000.00, 4.50, 240, '2024-03-01', 'ACTIVE',    NOW(), 0),
(2,  'LN20240002', 2, 2, 'AUTO',      200000.00, 5.00, 60,  '2024-05-15', 'ACTIVE',    NOW(), 0),
(3,  'LN20240003', 3, 9, 'PERSONAL',   50000.00, 7.00, 36,  '2024-06-01', 'ACTIVE',    NOW(), 0),
(4,  'LN20240004', 4, 2, 'BUSINESS', 2000000.00, 5.50, 120, '2024-01-10', 'ACTIVE',    NOW(), 0),
(5,  'LN20240005', 1, 1, 'PERSONAL',   30000.00, 6.50, 12,  '2024-08-01', 'PAID_OFF',  NOW(), 0),
(6,  'LN20250001', 5, 9, 'HOUSING',   600000.00, 4.20, 180, '2025-02-01', 'ACTIVE',    NOW(), 0);
