-- =====================================================
-- agent_platform 数据库初始化脚本（UTF-8 干净版）
-- 由 Docker 容器首次启动时自动执行
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET TIME_ZONE = '+08:00';

-- ----------------------------
-- 会议室表
-- ----------------------------
DROP TABLE IF EXISTS `meeting_room`;
CREATE TABLE `meeting_room` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `room_name`   varchar(50) NOT NULL           COMMENT '会议室名称',
  `floor`       int         NOT NULL           COMMENT '楼层',
  `capacity`    int         NOT NULL           COMMENT '容纳人数',
  `facilities`  varchar(200) DEFAULT NULL      COMMENT '设备（投影仪/白板等）',
  `status`      int          DEFAULT '1'       COMMENT '1可用 0维护中',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     int          DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议室表';

INSERT INTO `meeting_room` VALUES
  (1, '301会议室', 3, 10, '投影仪,白板',              1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, '302会议室', 3, 20, '投影仪,视频会议',           1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (3, '201会议室', 2,  8, '白板',                      1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (4, '501大会议室', 5, 50, '投影仪,音响,视频会议',    1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- 会议/日程预约表
-- ----------------------------
DROP TABLE IF EXISTS `meeting_schedule`;
CREATE TABLE `meeting_schedule` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `room_id`    bigint       NOT NULL           COMMENT '会议室ID（0=个人日程）',
  `booker`     varchar(50)  NOT NULL           COMMENT '预约人',
  `start_time` datetime     NOT NULL           COMMENT '开始时间',
  `end_time`   datetime     NOT NULL           COMMENT '结束时间',
  `topic`      varchar(200) DEFAULT NULL       COMMENT '主题',
  `status`     int          DEFAULT '1'        COMMENT '1有效 0取消',
  `create_time` datetime    DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    int          DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会议/日程预约表';

-- ----------------------------
-- 用户表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `username`    varchar(50) NOT NULL           COMMENT '登录用户名',
  `password`    varchar(100) NOT NULL          COMMENT 'BCrypt 加密密码',
  `real_name`   varchar(50)  DEFAULT NULL      COMMENT '真实姓名',
  `role`        varchar(20)  DEFAULT 'user'    COMMENT '角色：admin/user',
  `status`      int          DEFAULT '1'       COMMENT '1启用 0禁用',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统用户表';

-- 默认账号: admin / 123456（BCrypt 加密）
INSERT INTO `sys_user` VALUES
  (1, 'admin', '$2a$10$bdMiyhFCbgwNNG54h69C5OswCHC4458VEMYcLJ/GI8iR/O7bxxFdy', '管理员', 'admin', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- 角色表
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          bigint      NOT NULL AUTO_INCREMENT,
  `role_code`   varchar(50) NOT NULL           COMMENT '角色编码',
  `role_name`   varchar(50) NOT NULL           COMMENT '角色名称',
  `description` varchar(250) DEFAULT NULL      COMMENT '角色描述',
  `status`      int          DEFAULT '1'       COMMENT '1启用 0禁用',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统角色表';

INSERT INTO `sys_role` VALUES
  (1, 'ROLE_ADMIN', '系统管理员', '管理系统内的员工账号与权限配置', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'ROLE_USER', '普通员工', '使用Agent进行知识库检索、代码或工具调用', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- 权限表
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `perm_code`     varchar(100) NOT NULL          COMMENT '权限编码 (如 user:view)',
  `perm_name`     varchar(100) NOT NULL          COMMENT '权限名称',
  `resource_path` varchar(200) DEFAULT NULL      COMMENT '请求接口路径',
  `method`        varchar(10)  DEFAULT '*'       COMMENT '请求方法 (GET/POST/PUT/DELETE/*)',
  `type`          tinyint      DEFAULT '1'       COMMENT '1菜单/目录 2接口/按钮',
  `parent_id`     bigint       DEFAULT '0'       COMMENT '父级权限ID',
  `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       tinyint      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB AUTO_INCREMENT=7
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统权限表';

INSERT INTO `sys_permission` VALUES
  (1, 'user:view',   '查看员工列表',   '/admin/users',       'GET',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (2, 'user:role',   '分配员工角色',   '/admin/user/role',   'POST', 2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (3, 'user:status', '启用禁用员工',   '/admin/user/status', 'PUT',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (4, 'role:view',   '查看角色列表',   '/admin/roles',       'GET',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (5, 'task:submit', '提交Agent任务',  '/task/submit',       'POST', 2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0),
  (6, 'task:query',  '查询Agent任务',  '/task/**',           'GET',  2, 0, '2026-06-05 15:51:24', '2026-06-05 15:51:24', 0);

-- ----------------------------
-- 用户与角色关联表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- admin 账号默认分配 ROLE_ADMIN 角色
INSERT INTO `sys_user_role` VALUES (1, 1);

-- ----------------------------
-- 角色与权限关联表
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `perm_id` bigint NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`role_id`,`perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- 系统管理员拥有全部权限
INSERT INTO `sys_role_permission` VALUES
  (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6);

-- 普通员工仅拥有提交和查询 Agent 任务权限
INSERT INTO `sys_role_permission` VALUES
  (2, 5), (2, 6);

SET FOREIGN_KEY_CHECKS = 1;

