#!/bin/bash
# ============================================================
# kill-ports.sh — 自动检测并清理本地开发环境占用的端口进程
# ============================================================

# 包含开发环境中的所有服务端口：
# 3000: Web UI
# 8080: Gateway
# 8081: User Service
# 8082: Task Service
# 8083: Tool Agent
# 8084: Code Agent
# 8090: Code Agent Python (infer_server)
PORTS=(3000 8080 8081 8082 8083 8084 8090)

echo "🔍 正在检查本地开发端口占用情况..."

for PORT in "${PORTS[@]}"; do
    # 查找占用该端口的 PID（可能返回多个，以换行分隔）
    PIDS=$(lsof -t -i:"$PORT")
    if [ -n "$PIDS" ]; then
        echo "💥 发现端口 $PORT 被以下进程占用，正在强制终止："
        # 逐个展示进程详情
        lsof -i:"$PORT"
        # 强制杀死进程
        kill -9 $PIDS 2>/dev/null
        echo "✅ 端口 $PORT 已成功释放。"
        echo "----------------------------------------"
    else
        echo "🟢 端口 $PORT 正常 (未被占用)"
    fi
done

# 停止本地 docker compose 基础设施
echo "🐳 正在停止本地 Docker 数据库与缓存容器 (MySQL + Redis)..."
docker compose stop mysql redis 2>/dev/null

echo "🎉 所有端口冲突已清理完毕！您可以重新运行 bash dev.sh 启动开发环境了。"
