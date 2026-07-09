#!/bin/bash
# ============================================================
# mydev.sh — 一键启动本地开发环境
# 用法：bash mydev.sh
# 会自动打开多个终端窗口，每个跑一个服务
# ============================================================

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Load environment variables from .env if present
if [ -f "$PROJECT_DIR/.env" ]; then
    export $(grep -v '^#' "$PROJECT_DIR/.env" | xargs)
    export DB_USER=$DB_USERNAME
fi

# ⚠️  强制 Maven 使用 JDK 17（Homebrew 安装 of Maven 默认绑定 Java 26，会导致 Lombok 崩溃）
export JAVA_HOME=/Users/mitsuhahi/Library/Java/JavaVirtualMachines/ms-17.0.19/Contents/Home

echo "🚀 启动基础设施（MySQL + Redis）..."
docker compose up -d mysql redis

echo "⏳ 等待 MySQL 初始化（15 秒）..."
sleep 15

# ── 数据库补丁：确保 sys_config 表存在（用于会话超时配置）─────────
echo "🛠  Running DB migrations (sys_config table)..."
docker exec agent_mysql mysql -uroot -p"${DB_PASSWORD:-123456}" "${DB_NAME:-agent_platform}" 2>/dev/null <<'SQL'
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `param_key`   varchar(100) NOT NULL COMMENT 'Config key',
  `param_value` varchar(500) NOT NULL COMMENT 'Config value',
  `description` varchar(250) DEFAULT NULL COMMENT 'Description',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_param_key` (`param_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System Configuration Table';

INSERT IGNORE INTO `sys_config` (`id`, `param_key`, `param_value`, `description`) VALUES
  (1, 'session_timeout', '30', 'Session inactivity timeout in minutes');
SQL

if [ $? -eq 0 ]; then
    echo "   ✅ DB migration OK — sys_config table is ready."
else
    echo "   ⚠️  DB migration skipped or failed (MySQL may not be ready yet — will retry on service start)."
fi

# ── 验证 Redis 连通性 ──────────────────────────────────────────────
echo "🔍 Verifying Redis connectivity..."
if docker exec agent_redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
    echo "   ✅ Redis is alive and responding."
else
    echo "   ⚠️  Redis did not respond. Check docker compose status."
fi

# 用 osascript 在新终端窗口里运行命令（Mac 专用）
open_new_terminal() {
    local title=$1
    local cmd=$2
    osascript <<EOF
tell application "Terminal"
    do script "echo '=== $title ==='; $cmd"
    activate
end tell
EOF
}

JDK17="export JAVA_HOME=/Users/mitsuhahi/Library/Java/JavaVirtualMachines/ms-17.0.19/Contents/Home"

echo "🔧 启动后端服务..."
open_new_terminal "Gateway (8080)"      "$JDK17 && cd '$PROJECT_DIR/gateway-service'  && mvn spring-boot:run -DskipTests"
sleep 1
open_new_terminal "User Service (8081)" "$JDK17 && cd '$PROJECT_DIR/user-service'     && mvn spring-boot:run -DskipTests"
sleep 1
open_new_terminal "Task Service (8082)" "$JDK17 && cd '$PROJECT_DIR/task-service'     && mvn spring-boot:run -DskipTests"
sleep 1
open_new_terminal "Tool Agent (8083)"   "$JDK17 && cd '$PROJECT_DIR/tool-agent'       && mvn spring-boot:run -DskipTests"
sleep 1
open_new_terminal "Code Agent (8084)"   "$JDK17 && cd '$PROJECT_DIR/code-agent'       && mvn spring-boot:run -DskipTests"
sleep 1
open_new_terminal "Code Agent Python (8090)" "export DB_PASSWORD='${DB_PASSWORD:-123456}' && export DB_USER='${DB_USERNAME:-root}' && cd '$PROJECT_DIR/code-agent/data'       && python3 infer_server.py"
sleep 1

echo "🎨 启动前端..."
open_new_terminal "Web UI (3000)"       "cd '$PROJECT_DIR/web-ui' && npm install && npm run dev"

echo ""
echo "✅ 所有服务已在独立窗口中启动！"
echo ""
echo "   前端:   http://localhost:3000"
echo "   网关:   http://localhost:8080"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🧪 Session 超时测试快捷命令："
echo ""
echo "  查看活跃 session keys:"
echo "    docker exec agent_redis redis-cli keys 'session:active:*'"
echo ""
echo "  查看某用户 session TTL（秒）:"
echo "    docker exec agent_redis redis-cli ttl 'session:active:<username>'"
echo ""
echo "  手动缩短 TTL 为 10 秒（快速测试超时）:"
echo "    docker exec agent_redis redis-cli expire 'session:active:<username>' 10"
echo ""
echo "  查看超时配置缓存:"
echo "    docker exec agent_redis redis-cli get 'sys:config:session_timeout'"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "停止服务：关闭对应终端窗口，然后运行："
echo "  docker compose stop mysql redis"
