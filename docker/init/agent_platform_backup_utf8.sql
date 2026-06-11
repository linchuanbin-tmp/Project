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
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统用户表';

-- 默认账号: admin / 123456（BCrypt 加密）
INSERT INTO `sys_user` VALUES
  (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dBlOeu0sOreFSQpT5e', '管理员', 'admin', 1, '2026-06-05 15:51:24', '2026-06-05 15:51:24');

SET FOREIGN_KEY_CHECKS = 1;
