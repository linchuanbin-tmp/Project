#!/bin/bash
# ============================================================
# deploy-prod.sh — 本地打包 + 同步 + 服务器重启
# 用法: bash deploy-prod.sh
# 详细说明见 docs/manual_deployment_guide.md
# ============================================================

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

SERVER="root@167.172.82.161"
SSH_PORT="22118"
REMOTE_DIR="/root/agent_platform"

echo "==> 1/5 编译后端 JAR（含 code-agent）"
export JAVA_HOME="${JAVA_HOME:-/Users/mitsuhahi/Library/Java/JavaVirtualMachines/ms-17.0.19/Contents/Home}"
./mvnw clean package -DskipTests
./mvnw -f code-agent/pom.xml clean package -DskipTests

echo "==> 2/5 编译前端 dist"
cd web-ui
npm install
npm run build
cd ..

echo "==> 3/5 清理 macOS 隐藏文件（防止 JAR 损坏，见 docs/deployment_and_security_notes.md 6.1）"
find . -name '._*' -delete 2>/dev/null || true

echo "==> 4/5 同步到服务器 ${SERVER}:${REMOTE_DIR}"
rsync -avz -e "ssh -p ${SSH_PORT}" \
  --exclude="node_modules" \
  --exclude=".git" \
  --exclude="*.iml" \
  --exclude=".idea" \
  ./ \
  "${SERVER}:${REMOTE_DIR}/"

echo "==> 5/5 服务器重启容器"
ssh -p "${SSH_PORT}" "${SERVER}" "cd ${REMOTE_DIR} && \
  find . -name '._*' -delete 2>/dev/null || true && \
  docker compose down && \
  docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build"

echo ""
echo "部署完成。请访问: http://167.172.82.161"
echo "若部门/文档/通知功能报错，请先在服务器执行数据库迁移 SQL（见 docs/manual_deployment_guide.md 步骤一）"
