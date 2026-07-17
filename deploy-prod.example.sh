#!/bin/bash
# ============================================================
# deploy-prod.sh - Build, sync, and restart on remote server
# Usage: bash deploy-prod.sh
#
# Fill in your own server details below before running.
# ============================================================

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# -- Server configuration (replace with your own values) --
SERVER="root@YOUR_SERVER_IP"
SSH_PORT="22"
REMOTE_DIR="/root/agent_platform"
SSH_PASSWORD="YOUR_SSH_PASSWORD"

# -- Java Home (adjust to your local JDK 17 path) --
export JAVA_HOME="${JAVA_HOME:-/path/to/jdk-17}"

echo "==> 1/5 Build backend JARs (including code-agent)"
./mvnw clean package -DskipTests
./mvnw -f code-agent/pom.xml clean package -DskipTests

echo "==> 2/5 Build frontend dist"
cd web-ui
npm install
npm run build
cd ..

echo "==> 3/5 Remove macOS hidden files (prevent JAR corruption)"
find . -name '._*' -delete 2>/dev/null || true

echo "==> 4/5 Sync to server ${SERVER}:${REMOTE_DIR}"
sshpass -p "${SSH_PASSWORD}" rsync -avz -e "ssh -p ${SSH_PORT}" \
  --exclude="node_modules" \
  --exclude=".git" \
  --exclude="*.iml" \
  --exclude=".idea" \
  --exclude=".env" \
  --exclude="certbot/" \
  ./ \
  "${SERVER}:${REMOTE_DIR}/"

echo "==> 5/5 Restart containers on server"
sshpass -p "${SSH_PASSWORD}" ssh -p "${SSH_PORT}" "${SERVER}" "cd ${REMOTE_DIR} && \
  find . -name '._*' -delete 2>/dev/null || true && \
  mkdir -p certbot/www certbot/conf && \
  docker compose down && \
  docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build"

echo ""
echo "Deployment complete. Visit: https://YOUR_DOMAIN"
echo ""

# ---- First run: obtain SSL certificate ----
echo ">>> Checking SSL certificate status..."
HAS_CERT=$(sshpass -p "${SSH_PASSWORD}" ssh -p "${SSH_PORT}" "${SERVER}" \
  "test -f ${REMOTE_DIR}/certbot/conf/live/YOUR_DOMAIN/fullchain.pem && echo yes || echo no")

if [ "$HAS_CERT" = "no" ]; then
  echo ">>> First deployment: requesting Let's Encrypt SSL certificate..."
  echo ">>> Make sure DNS A record points to your server IP first."
  echo ">>> Press Enter to continue..."
  read

  sshpass -p "${SSH_PASSWORD}" ssh -p "${SSH_PORT}" "${SERVER}" "\
    cd ${REMOTE_DIR} && \
    docker compose stop nginx && \
    docker run --rm \
      -v ${REMOTE_DIR}/certbot/www:/var/www/certbot \
      -v ${REMOTE_DIR}/certbot/conf:/etc/letsencrypt \
      -p 80:80 \
      certbot/certbot certonly --webroot -w /var/www/certbot \
      -d YOUR_DOMAIN -d www.YOUR_DOMAIN \
      --email admin@YOUR_DOMAIN --agree-tos --non-interactive --force-renewal || \
    docker run --rm \
      -v ${REMOTE_DIR}/certbot/www:/var/www/certbot \
      -v ${REMOTE_DIR}/certbot/conf:/etc/letsencrypt \
      -p 80:80 \
      certbot/certbot certonly --standalone \
      -d YOUR_DOMAIN -d www.YOUR_DOMAIN \
      --email admin@YOUR_DOMAIN --agree-tos --non-interactive"

  echo ">>> Certificate obtained, restarting nginx..."
  sshpass -p "${SSH_PASSWORD}" ssh -p "${SSH_PORT}" "${SERVER}" "\
    cd ${REMOTE_DIR} && \
    docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build nginx"
  echo ">>> HTTPS enabled! Visit: https://YOUR_DOMAIN"
else
  echo ">>> SSL certificate already exists, no renewal needed."
fi

echo ""
echo "If department/document/notification features throw errors, run DB migration SQL first (see docs/manual_deployment_guide.md Step 1)"
