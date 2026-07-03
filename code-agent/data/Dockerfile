FROM python:3.10-slim

WORKDIR /app

# Install dependencies
RUN pip install --no-cache-dir flask openai pymysql

# Copy service code
COPY . .

# Expose inference port
EXPOSE 8090

# Run inference service
CMD ["python", "infer_server.py"]
