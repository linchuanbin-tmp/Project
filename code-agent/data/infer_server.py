"""Lightweight Text-to-SQL Inference Server"""
import os
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"
from flask import Flask, request, jsonify
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
import torch

MODEL_PATH = "./fine-tuned-t5-banking"
MAX_LENGTH = 512

SCHEMA = (
    "tables:\n"
    "CREATE TABLE bank_customer (id BIGINT, customer_no VARCHAR(20), name VARCHAR(50), id_card VARCHAR(18), phone VARCHAR(15), risk_level VARCHAR(10), status INT); "
    "CREATE TABLE bank_account (id BIGINT, account_no VARCHAR(30), customer_id BIGINT, account_type VARCHAR(20), balance DECIMAL(18,2), currency VARCHAR(5), open_date DATE, status INT); "
    "CREATE TABLE bank_transaction (id BIGINT, txn_no VARCHAR(40), account_id BIGINT, txn_type VARCHAR(20), amount DECIMAL(18,2), counterparty_account VARCHAR(30), remark VARCHAR(100), txn_time DATETIME)\n"
)

print("Loading model...")
tok = AutoTokenizer.from_pretrained("t5-small")
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_PATH)
model.eval()
print("Model loaded. Starting server on :8090")

app = Flask(__name__)

@app.route("/infer", methods=["POST"])
def infer():
    data = request.get_json()
    question = data.get("question", "")
    if not question:
        return jsonify({"error": "question required"}), 400

    inp = tok(SCHEMA + "query for: " + question,
              return_tensors="pt", truncation=True, max_length=MAX_LENGTH)
    with torch.no_grad():
        output = model.generate(**inp, max_length=MAX_LENGTH)
    sql = tok.decode(output[0], skip_special_tokens=True).strip()
    return jsonify({"sql": sql, "method": "T5-SMALL-TEXT2SQL"})

@app.route("/health")
def health():
    return jsonify({"status": "UP", "model": "cssupport/t5-small-awesome-text-to-sql"})

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8090, debug=False)
