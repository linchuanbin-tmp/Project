#!/bin/bash

# 自动检测项目根目录（脚本所在目录）
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOGS_DIR="$PROJECT_DIR/logs"
MYSQL_DATA_DIR="${MYSQL_DATA_DIR:-/opt/homebrew/var/mysql}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

mkdir -p "$LOGS_DIR"

# ========================================
# 1. 终止所有旧进程
# ========================================
log_info "终止已有服务..."

# 终止项目后端及前端进程（按端口）
for port in 8080 8081 8082 8083 8084 8090 3000; do
  pids=$(lsof -ti:$port 2>/dev/null || true)
  if [ -n "$pids" ]; then
    for pid in $pids; do
      log_warn "端口 $port 被占用 (PID: $pid)，终止..."
      kill -15 "$pid" 2>/dev/null || true
    done
  fi
done

sleep 2

# 强制终止残留
for port in 8080 8081 8082 8083 8084 8090 3000; do
  pids=$(lsof -ti:$port 2>/dev/null || true)
  if [ -n "$pids" ]; then
    for pid in $pids; do
      kill -9 "$pid" 2>/dev/null || true
    done
  fi
done

# 终止旧的 Redis
if ! redis-cli ping 2>/dev/null; then
  pkill -9 redis-server 2>/dev/null || true
fi

# ========================================
# 2. 启动 MySQL（直接启动 mysqld，绕过不稳定的 brew services）
# ========================================
log_info "启动 MySQL..."

# 先清理残留的 MySQL 进程（避免 ibdata1 锁冲突）
pkill -9 -f mysqld 2>/dev/null || true
pkill -9 -f mysqld_safe 2>/dev/null || true
sleep 2

if mysqladmin ping -u root -p123456 2>/dev/null; then
  log_info "MySQL 已在运行"
else
  mysqld --daemonize --user=_mysql --datadir="$MYSQL_DATA_DIR" --port=3306 2>/dev/null

  for i in $(seq 1 30); do
    if mysqladmin ping -u root -p123456 2>/dev/null; then
      log_info "MySQL 启动成功"
      break
    fi
    if [ $i -eq 30 ]; then
      log_error "MySQL 启动失败，请检查: tail -20 $MYSQL_DATA_DIR/*.err"
      exit 1
    fi
    sleep 1
  done
fi

# ========================================
# 3. 启动 Redis
# ========================================
log_info "启动 Redis..."

if redis-cli ping 2>/dev/null; then
  log_info "Redis 已在运行"
else
  redis-server --daemonize yes --port 6379 2>/dev/null
  sleep 1

  if redis-cli ping 2>/dev/null; then
    log_info "Redis 启动成功"
  else
    log_error "Redis 启动失败"
    exit 1
  fi
fi

# ========================================
# 4. 编译后端
# ========================================
log_info "编译所有后端模块（首次较慢，请耐心等待）..."

cd "$PROJECT_DIR"

# 确保 mvnw 有执行权限
[ ! -x ./mvnw ] && chmod +x ./mvnw

./mvnw -pl gateway-service,user-service,task-service,tool-agent -am install -DskipTests -q > "$LOGS_DIR/build.log" 2>&1
if [ $? -ne 0 ]; then
  log_error "编译失败，请查看日志: $LOGS_DIR/build.log"
  exit 1
fi

# 编译 code-agent（独立 Spring Boot 3.2.0 项目）
log_info "编译 code-agent..."
./mvnw -f code-agent/pom.xml install -DskipTests -q > "$LOGS_DIR/build-code-agent.log" 2>&1
if [ $? -ne 0 ]; then
  log_error "code-agent 编译失败，请查看日志: $LOGS_DIR/build-code-agent.log"
  exit 1
fi
log_info "编译完成"

# ========================================
# 5. 启动后端服务
# ========================================
log_info "启动后端服务..."

start_service() {
  local name=$1
  local module=$2
  local port=$3

  log_info "启动 $name (端口 $port)..."
  nohup ./mvnw -pl "$module" spring-boot:run \
    > "$LOGS_DIR/${module}.log" 2>&1 &

  for i in $(seq 1 60); do
    if lsof -ti:$port > /dev/null 2>&1; then
      local pid=$(lsof -ti:$port 2>/dev/null || true)
      log_info "$name 启动成功 (PID: $pid)"
      return 0
    fi
    sleep 2
  done
  log_error "$name 启动超时，请查看日志: $LOGS_DIR/${module}.log"
}

start_service "gateway-service" "gateway-service" 8080
start_service "user-service"   "user-service"   8081
start_service "task-service"   "task-service"   8082
# code-agent 是独立 Spring Boot 项目，单独启动
log_info "启动 code-agent (端口 8084)..."
nohup ./mvnw -f code-agent/pom.xml spring-boot:run \
  > "$LOGS_DIR/code-agent.log" 2>&1 &

for i in $(seq 1 60); do
  if lsof -ti:8084 > /dev/null 2>&1; then
    local pid=$(lsof -ti:8084 2>/dev/null || true)
    log_info "code-agent 启动成功 (PID: $pid)"
    break
  fi
  if [ $i -eq 60 ]; then
    log_error "code-agent 启动超时，请查看日志: $LOGS_DIR/code-agent.log"
  fi
  sleep 2
done

# ========================================
# 6. 启动 Python 推理服务（code-agent 依赖）
# ========================================
INFER_DIR="$PROJECT_DIR/code-agent/data"

if [ -f "$INFER_DIR/ds_config.json" ]; then
  log_info "检查 Python 推理服务依赖..."

  # 检查 Python 环境
  if command -v python3 &>/dev/null; then
    PYTHON=python3
  elif command -v python &>/dev/null; then
    PYTHON=python
  else
    log_error "未找到 Python，请安装 Python 3.11+"
    exit 1
  fi

  # 安装依赖（静默）
  $PYTHON -m pip install flask openai pymysql -q 2>/dev/null

  # 启动推理服务
  log_info "启动 Python 推理服务 (端口 8090)..."
  cd "$INFER_DIR"
  nohup $PYTHON infer_server.py > "$LOGS_DIR/infer-server.log" 2>&1 &
  cd "$PROJECT_DIR"

  for i in $(seq 1 15); do
    if lsof -ti:8090 > /dev/null 2>&1; then
      log_info "推理服务启动成功 (http://localhost:8090)"
      break
    fi
    if [ $i -eq 15 ]; then
      log_warn "推理服务启动超时，请检查日志: $LOGS_DIR/infer-server.log"
    fi
    sleep 1
  done
else
  log_warn "未找到 ds_config.json，跳过推理服务启动"
fi

# ========================================
# 7. 启动前端
# ========================================
log_info "启动前端 (端口 3000)..."

cd "$PROJECT_DIR/web-ui"

if [ ! -d "node_modules" ]; then
  log_info "安装前端依赖..."
  npm install
fi

nohup npm run dev > "$LOGS_DIR/web-ui.log" 2>&1 &

for i in $(seq 1 30); do
  if lsof -ti:3000 > /dev/null 2>&1; then
    log_info "前端启动成功 (http://localhost:3000)"
    break
  fi
  sleep 1
done

# ========================================
# 完成
# ========================================
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  所有服务已启动！${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "  MySQL:        ${YELLOW}localhost:3306${NC}"
echo -e "  Redis:        ${YELLOW}localhost:6379${NC}"
echo -e "  Gateway:      ${YELLOW}http://localhost:8080${NC}"
echo -e "  User Service: ${YELLOW}http://localhost:8081${NC}"
echo -e "  Task Service: ${YELLOW}http://localhost:8082${NC}"
echo -e "  Tool Agent:   ${YELLOW}http://localhost:8083${NC}"
echo -e "  Code Agent:   ${YELLOW}http://localhost:8084${NC}"
echo -e "  推理服务:     ${YELLOW}http://localhost:8090${NC}"
echo -e "  前端页面:     ${YELLOW}http://localhost:3000${NC}"
echo ""
echo -e "  日志目录:     ${YELLOW}$LOGS_DIR${NC}"
echo -e "  停止服务:     ${YELLOW}./stop.sh${NC}"
echo ""
