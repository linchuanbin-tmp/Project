FROM node:20-alpine
WORKDIR /app

# 先复制 package.json（利用 layer 缓存，依赖不变时不重新 npm install）
COPY package*.json ./
RUN npm install

# 复制源码
COPY . .

EXPOSE 3000

# --host 0.0.0.0 让 Vite 在容器内对外可访问
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "3000"]
