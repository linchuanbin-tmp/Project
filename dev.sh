#!/bin/bash
# ============================================================
# dev.sh — General-purpose development environment startup script
# Usage: bash dev.sh
# Automatically starts database containers and launches all services
# in separate terminal windows (supports macOS & Linux) or in the
# background as a fallback.
# ============================================================

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# 1. Load environment variables from .env if present
if [ -f ".env" ]; then
    echo "📝 Loading environment variables from .env..."
    # Export all variables from .env (excluding comments)
    export $(grep -v '^#' .env | xargs)
    export DB_USER=${DB_USERNAME:-root}
fi

# 2. Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker first."
    exit 1
fi

# 3. Detect OS Type
OS_TYPE="$(uname -s 2>/dev/null || echo "unknown")"

# 4. Auto-detect Java Home (JDK 17 required)
if [ -z "${JAVA_HOME:-}" ]; then
    if [ "$OS_TYPE" = "Darwin" ] && [ -x "/usr/libexec/java_home" ]; then
        # On macOS, attempt to find JDK 17, fallback to default java_home
        export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home 2>/dev/null)
    fi
fi

if [ -n "${JAVA_HOME:-}" ]; then
    echo "☕ Found JAVA_HOME: $JAVA_HOME"
    JDK_CMD="export JAVA_HOME='$JAVA_HOME'"
else
    echo "⚠️  JAVA_HOME is not set. Using system default Java."
    JDK_CMD="echo 'Using system default Java'"
fi

# 5. Detect Python command for Code Agent
PYTHON_CMD="python3"
if ! command -v python3 >/dev/null 2>&1; then
    if command -v python >/dev/null 2>&1; then
        PYTHON_CMD="python"
    else
        echo "⚠️  Neither python3 nor python was found in PATH. Code Agent Python may fail to start."
    fi
fi

# 6. Ensure Maven wrapper is executable
if [ -f "./mvnw" ]; then
    chmod +x ./mvnw
    MVN_CMD="./mvnw"
else
    MVN_CMD="mvn"
fi

# 7. Start infrastructure services
echo "🚀 Starting infrastructure (MySQL + Redis)..."
docker compose up -d mysql redis

echo "⏳ Waiting for MySQL to initialize (10 seconds)..."
sleep 10

# 8. Define terminal opening function
open_new_terminal() {
    local title=$1
    local cmd=$2
    
    if [ "$OS_TYPE" = "Darwin" ]; then
        # macOS: AppleScript to open a new terminal window
        osascript <<EOF
tell application "Terminal"
    do script "echo '=== $title ==='; $cmd"
    activate
end tell
EOF
    elif command -v gnome-terminal >/dev/null 2>&1; then
        # Linux (GNOME Terminal)
        gnome-terminal --title="$title" -- bash -c "echo '=== $title ==='; $cmd; exec bash" &
    elif command -v xterm >/dev/null 2>&1; then
        # Linux (Xterm fallback)
        xterm -T "$title" -e bash -c "echo '=== $title ==='; $cmd; exec bash" &
    else
        # Fallback to background execution with logs
        echo "⚠️  No graphical terminal emulator found. Running $title in background..."
        mkdir -p "$PROJECT_DIR/logs"
        local log_file="$PROJECT_DIR/logs/${title// /_}.log"
        nohup bash -c "echo '=== $title ==='; $cmd" > "$log_file" 2>&1 &
        echo "   👉 Logs redirected to: $log_file"
    fi
}

# 9. Launch Backend Services
echo "🔧 Launching backend microservices..."
open_new_terminal "Gateway (8080)"      "$JDK_CMD && cd '$PROJECT_DIR/gateway-service'  && $MVN_CMD spring-boot:run -DskipTests"
sleep 1
open_new_terminal "User Service (8081)" "$JDK_CMD && cd '$PROJECT_DIR/user-service'     && $MVN_CMD spring-boot:run -DskipTests"
sleep 1
open_new_terminal "Task Service (8082)" "$JDK_CMD && cd '$PROJECT_DIR/task-service'     && $MVN_CMD spring-boot:run -DskipTests"
sleep 1
open_new_terminal "Tool Agent (8083)"   "$JDK_CMD && cd '$PROJECT_DIR/tool-agent'       && $MVN_CMD spring-boot:run -DskipTests"
sleep 1
open_new_terminal "Code Agent (8084)"   "$JDK_CMD && cd '$PROJECT_DIR/code-agent'       && $MVN_CMD spring-boot:run -DskipTests"
sleep 1
open_new_terminal "Code Agent Python (8090)" "export DB_PASSWORD='${DB_PASSWORD:-123456}' && export DB_USER='${DB_USER:-root}' && cd '$PROJECT_DIR/code-agent/data' && $PYTHON_CMD infer_server.py"
sleep 1

# 10. Launch Frontend
echo "🎨 Launching frontend..."
open_new_terminal "Web UI (3000)"       "cd '$PROJECT_DIR/web-ui' && npm install && npm run dev"

echo ""
echo "✅ All services initiated!"
echo ""
echo "   Frontend Web UI:  http://localhost:3000"
echo "   API Gateway:      http://localhost:8080"
echo ""
echo "To stop infrastructure services, run:"
echo "  docker compose stop mysql redis"
echo ""
