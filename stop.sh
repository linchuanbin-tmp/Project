#!/bin/bash

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

log_info "正在停止所有服务..."

# 1. 停止后端（端口 8080-8084）+ 推理服务（8090）
for port in 8080 8081 8082 8083 8084 8090; do
  pids=$(lsof -ti:$port 2>/dev/null || true)
  if [ -n "$pids" ]; then
    for pid in $pids; do
      log_info "终止端口 $port 上的进程 (PID: $pid)"
      kill -15 "$pid" 2>/dev/null || true
    done
  fi
done

# 2. 停止前端（端口 3000）
frontend_pids=$(lsof -ti:3000 2>/dev/null || true)
if [ -n "$frontend_pids" ]; then
  for pid in $frontend_pids; do
    log_info "终止前端进程 (PID: $pid)"
    kill -15 "$pid" 2>/dev/null || true
  done
fi

# 等待优雅退出
sleep 2

# 3. 强制终止残留
for port in 8080 8081 8082 8083 8084 8090 3000; do
  pids=$(lsof -ti:$port 2>/dev/null || true)
  if [ -n "$pids" ]; then
    for pid in $pids; do
      log_info "强制终止端口 $port 残留进程 (PID: $pid)"
      kill -9 "$pid" 2>/dev/null || true
    done
  fi
done

# 4. 停止 Redis
if redis-cli ping 2>/dev/null; then
  redis-cli shutdown 2>/dev/null || true
  sleep 1
  pkill -9 redis-server 2>/dev/null || true
  log_info "Redis 已停止"
fi

# 5. 停止 MySQL
pkill -15 mysqld 2>/dev/null || true
sleep 2
pkill -9 mysqld 2>/dev/null || true
pkill -9 -f mysqld_safe 2>/dev/null || true

log_info "所有服务已停止"
