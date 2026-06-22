"""Execution Accuracy - compares result sets, not strings"""
import json, torch, os
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM

MODEL_PATH = "./fine-tuned-t5-banking"
TOKENIZER_NAME = "./fine-tuned-t5-banking"
DATASET_PATH = "text2sql_dataset.json"
MAX_LENGTH = 512

SCHEMA = (
    "tables:\n"
    "CREATE TABLE bank_customer (id BIGINT, customer_no VARCHAR(20), name VARCHAR(50), id_card VARCHAR(18), phone VARCHAR(15), risk_level VARCHAR(10), status INT); "
    "CREATE TABLE bank_account (id BIGINT, account_no VARCHAR(30), customer_id BIGINT, account_type VARCHAR(20), balance DECIMAL(18,2), currency VARCHAR(5), open_date DATE, status INT); "
    "CREATE TABLE bank_transaction (id BIGINT, txn_no VARCHAR(40), account_id BIGINT, txn_type VARCHAR(20), amount DECIMAL(18,2), counterparty_account VARCHAR(30), remark VARCHAR(100), txn_time DATETIME)\n"
)

print("Loading MySQL...")
try:
    import pymysql
    conn = pymysql.connect(host="localhost", user="root", password="zzm20030718",
                           database="agent_platform", charset="utf8mb4")
except:
    conn = None
    print("No MySQL, string match only")

def run(sql):
    if conn is None:
        return None
    try:
        with conn.cursor() as c:
            c.execute(sql)
            return tuple(sorted(c.fetchall()))
    except Exception as e:
        return None

print("Loading model...")
tok = AutoTokenizer.from_pretrained(TOKENIZER_NAME)
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_PATH)
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model.to(device); model.eval()

with open(DATASET_PATH, "r", encoding="utf-8") as f:
    data = json.load(f)
test = data[int(len(data)*0.8):]

print(f"\nEval {len(test)} examples...\n")
exact = exec_ok = errs = 0
for i, item in enumerate(test):
    inp = tok(SCHEMA + "query for: " + item["question"],
              return_tensors="pt", truncation=True, max_length=MAX_LENGTH).to(device)
    with torch.no_grad():
        pred = tok.decode(model.generate(**inp, max_length=MAX_LENGTH)[0],
                          skip_special_tokens=True).strip()
    gold = item["query"].strip()
    e = pred.lower() == gold.lower()
    if e: exact += 1

    gr, pr = run(gold), run(pred)
    if pr is None: errs += 1; s = "ERR"
    elif gr == pr: exec_ok += 1; s = "OK"
    else: s = "DIFF"

    if i < 15:
        print(f"[{i+1:3d}] {'MATCH' if e else s:6s} | {item['question'][:65]}")
        if not e and s == "DIFF":
            print(f"       P: {pred[:85]}")

print(f"\n{'='*55}")
print(f"Exact Match:     {exact}/{len(test)} = {exact/len(test)*100:.1f}%")
if conn:
    print(f"Execution Match: {exec_ok}/{len(test)} = {exec_ok/len(test)*100:.1f}%")
    print(f"SQL Errors:      {errs}/{len(test)}")
    print(f"Any Correct:     {exact+exec_ok}/{len(test)} = {(exact+exec_ok)/len(test)*100:.1f}%")
    conn.close()
