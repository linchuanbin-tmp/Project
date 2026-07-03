#!/bin/bash
# ============================================================
# dev.sh — 一键启动本地开发环境
# 用法：bash dev.sh
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
echo "   前端:  http://localhost:3000"
echo "   网关:  http://localhost:8080"
echo ""
echo "停止服务：关闭对应终端窗口，然后运行："
echo "  docker compose stop mysql redis"
