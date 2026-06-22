# 模型目录（预留）
#
# 当前架构：Java 后端通过 HTTP 调用 Python 推理服务器（data/infer_server.py）
# 模型权重存储在 data/fine-tuned-t5-banking/
#
# 如需 ONNX 运行时推理，可将 PyTorch 模型导出为 ONNX 格式：
#   python -m transformers.onnx --model=data/fine-tuned-t5-banking text2sql.onnx
#   - text2sql.onnx       (模型文件)
#   - tokenizer.json      (tokenizer 配置, train.py 自动保存)
#
# 接入 Code Agent:
#   1. 将 text2sql.onnx 放在此目录
#   2. 修改 application.yml: code-agent.onnx.enabled=true
#   3. 重启服务
