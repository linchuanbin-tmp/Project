"""
Text-to-SQL Fine-tuning — flan-t5-base v2
==========================================
改进：紧凑 schema、少 epoch、惩罚重复、简洁 prompt
"""

import json, os
import torch
from torch.utils.data import DataLoader
from transformers import (
    AutoTokenizer, AutoModelForSeq2SeqLM,
    get_linear_schedule_with_warmup,
)
from datasets import Dataset

torch.manual_seed(42)

# ===== 配置 =====
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_NAME = "juierror/flan-t5-text2sql-with-schema"  # 250M, Spider 预训练
BATCH_SIZE = 2
DATASET_PATH = os.path.join(BASE_DIR, "text2sql_dataset.json")
DISTILLED_PATH = os.path.join(BASE_DIR, "distilled_dataset.json")
OUTPUT_DIR = os.path.join(BASE_DIR, "fine-tuned-flan-t5")
EPOCHS = 8
LR = 3e-5                # 稍高补偿少 epoch
MAX_LENGTH = 512

# 紧凑一行式 schema（T5 系列对此格式理解更好）
SCHEMA = (
    "bank_customer: id customer_no name id_card phone risk_level status create_time update_time deleted | "
    "bank_account: id account_no customer_id account_type balance currency open_date status create_time deleted | "
    "bank_transaction: id txn_no account_id txn_type amount balance_after counterparty_account remark txn_time create_time | "
    "bank_department: id dept_name manager_id floor create_time deleted | "
    "bank_employee: id emp_no name dept_id position salary hire_date phone status create_time deleted | "
    "bank_loan: id loan_no customer_id emp_id loan_type amount interest_rate term_months start_date status create_time deleted"
)

# ===== Step 1: Load Data =====
print("Step 1: Loading dataset")
data_path = DISTILLED_PATH if os.path.exists(DISTILLED_PATH) else DATASET_PATH
print(f"  Source: {data_path}")
with open(data_path, "r", encoding="utf-8") as f:
    raw = json.load(f)
split = int(len(raw) * 0.8)
train_raw, test_raw = raw[:split], raw[split:]
print(f"  Train: {len(train_raw)}, Test: {len(test_raw)}")

# ===== Step 2: Load Model =====
print("Step 2: Loading flan-t5-base...")
tok = AutoTokenizer.from_pretrained(MODEL_NAME)
if tok.pad_token is None:
    tok.pad_token = tok.eos_token
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_NAME)
print(f"  Params: {sum(p.numel() for p in model.parameters()):,}")

# ===== Step 3: Tokenize =====
def collate_fn(batch):
    # 简洁 prompt，与推理时一致
    qs = ["question: " + b["question"] + " | schema: " + SCHEMA for b in batch]
    sq = [b["query"] for b in batch]
    inp = tok(qs, max_length=MAX_LENGTH, truncation=True, padding=True, return_tensors="pt")
    out = tok(sq, max_length=MAX_LENGTH, truncation=True, padding=True, return_tensors="pt")
    out[out == tok.pad_token_id] = -100
    return {"input_ids": inp["input_ids"], "attention_mask": inp["attention_mask"], "labels": out["input_ids"]}

# ===== Step 4: Train =====
print("Step 3: Training")
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model.to(device)

optimizer = torch.optim.AdamW(model.parameters(), lr=LR)
loader = DataLoader(
    Dataset.from_list(train_raw), batch_size=BATCH_SIZE, shuffle=True,
    collate_fn=collate_fn,
)
total_steps = len(loader) * EPOCHS
scheduler = get_linear_schedule_with_warmup(
    optimizer, num_warmup_steps=total_steps // 10, num_training_steps=total_steps
)

best_loss = float("inf")
for epoch in range(EPOCHS):
    model.train()
    total_loss = 0
    for step, batch in enumerate(loader):
        batch = {k: v.to(device) for k, v in batch.items()}
        loss = model(**batch).loss
        loss.backward()
        optimizer.step()
        scheduler.step()
        optimizer.zero_grad()
        total_loss += loss.item()
        if step % 5 == 0:
            print(f"  Epoch {epoch+1}/{EPOCHS} Step {step}/{len(loader)} Loss: {loss.item():.4f}")

    avg_loss = total_loss / len(loader)
    print(f"  >>> Epoch {epoch+1} done, avg loss: {avg_loss:.4f}")
    if avg_loss < best_loss:
        best_loss = avg_loss
        model.save_pretrained(OUTPUT_DIR)
        tok.save_pretrained(OUTPUT_DIR)
        print(f"  Saved (best loss: {best_loss:.4f})")

# ===== Step 5: Quick Test =====
print("\nStep 4: Quick test")
model.eval()
correct = 0
for i in range(min(10, len(test_raw))):
    prompt = "question: " + test_raw[i]["question"] + " | schema: " + SCHEMA
    inp = tok(prompt, return_tensors="pt", truncation=True, max_length=MAX_LENGTH).to(device)
    with torch.no_grad():
        out = model.generate(
            **inp, max_length=128,
            num_beams=3, early_stopping=True,
            repetition_penalty=1.5,        # 防重复
            no_repeat_ngram_size=3,        # 防 3-gram 重复
        )
    pred = tok.decode(out[0], skip_special_tokens=True).strip()
    expected = test_raw[i]["query"].strip()
    match = pred.lower() == expected.lower()
    if match:
        correct += 1
    print(f"  [{i+1}] {'✅' if match else '❌'} Q: {test_raw[i]['question']}")
    print(f"       Exp: {expected}")
    print(f"       Got: {pred}")

print(f"\n  Score: {correct}/{min(10, len(test_raw))}")
print(f"Done! Model: {OUTPUT_DIR}")
