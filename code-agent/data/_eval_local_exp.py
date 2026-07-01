"""Execution Accuracy — 本地 flan-t5-text2sql 模型评测"""
import json, torch, os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM

MODEL_PATH = "./fine-tuned-flan-t5"
DATASET_PATH = "distilled_dataset.json"
MAX_LENGTH = 512

# 紧凑 schema（与训练时一致）
SCHEMA = (
    "bank_customer: id customer_no name id_card phone risk_level status create_time update_time deleted | "
    "bank_account: id account_no customer_id account_type balance currency open_date status create_time deleted | "
    "bank_transaction: id txn_no account_id txn_type amount balance_after counterparty_account remark txn_time create_time | "
    "bank_department: id dept_name manager_id floor create_time deleted | "
    "bank_employee: id emp_no name dept_id position salary hire_date phone status create_time deleted | "
    "bank_loan: id loan_no customer_id emp_id loan_type amount interest_rate term_months start_date status create_time deleted"
)

# MySQL
print("Loading MySQL...")
try:
    import pymysql
    conn = pymysql.connect(host="localhost", user="root", password="zzm20030718",
                           database="agent_platform", charset="utf8mb4")
    print("✅ MySQL 已连接")
except Exception as e:
    conn = None
    print(f"⚠️ 无 MySQL ({e})")

def run(sql):
    if conn is None: return None
    try:
        with conn.cursor() as c:
            c.execute(sql)
            return tuple(sorted(c.fetchall()))
    except: return None

# 加载模型
print("Loading model...")
tok = AutoTokenizer.from_pretrained(MODEL_PATH)
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_PATH)
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model.to(device); model.eval()
print(f"  Device: {device}")

# 加载数据
with open(DATASET_PATH, "r", encoding="utf-8") as f:
    data = json.load(f)
test = data[int(len(data) * 0.8):]
print(f"\n🧪 评估 {len(test)} 条（本地模型 + MySQL 执行对比）\n")

exact = exec_ok = errs = 0
for i, item in enumerate(test):
    q = item["question"]
    gold = item["query"].strip()

    # 本地推理
    prompt = "question: " + q + " | schema: " + SCHEMA
    inp = tok(prompt, return_tensors="pt", truncation=True, max_length=MAX_LENGTH).to(device)
    with torch.no_grad():
        out = model.generate(**inp, max_length=128, num_beams=3, early_stopping=True,
                             repetition_penalty=1.5, no_repeat_ngram_size=3)
    pred = tok.decode(out[0], skip_special_tokens=True).strip()

    # 精确匹配
    e = pred.lower() == gold.lower()
    if e: exact += 1

    # MySQL 执行对比
    gr, pr = run(gold), run(pred)
    if pr is None:
        errs += 1; s = "ERR"
    elif gr == pr:
        exec_ok += 1; s = "OK"
    else:
        s = "DIFF"

    tag = "MATCH" if e else s
    if i < 20:
        print(f"[{i+1:3d}] {tag:6s} | {q[:55]}")
        if not e and s == "DIFF":
            print(f"       G: {gold[:75]}")
            print(f"       P: {pred[:75]}")

# 汇总
print(f"\n{'='*55}")
print(f"Exact Match:     {exact}/{len(test)} = {exact/len(test)*100:.1f}%")
if conn:
    print(f"Execution Match: {exec_ok}/{len(test)} = {exec_ok/len(test)*100:.1f}%")
    print(f"SQL Errors:      {errs}/{len(test)}")
    print(f"Any Correct:     {exact+exec_ok}/{len(test)} = {(exact+exec_ok)/len(test)*100:.1f}%")
    conn.close()
print(f"🏆 模型: juierror/flan-t5-text2sql-with-schema (250M)")
