"""
Manual Text-to-SQL Fine-tuning (zero extra deps)
=================================================
Pure torch + transformers. No Trainer, no accelerate, no peft.
Plain PyTorch training loop that actually works.
"""

import json, os
import torch
from torch.utils.data import DataLoader
from transformers import (
    AutoTokenizer, AutoModelForSeq2SeqLM,
    get_linear_schedule_with_warmup,
    set_seed,
)
from datasets import Dataset

set_seed(42)

# Use pre-fine-tuned Spider model weights with original t5-small tokenizer
# Use local pre-trained model (HuggingFace may be unreachable)
MODEL_NAME = "./pretrained_t5sql"
TOKENIZER_NAME = "./fine-tuned-t5-banking"  # has tokenizer files, local only
DATASET_PATH = "text2sql_dataset.json"
OUTPUT_DIR = "./fine-tuned-t5-banking"
BATCH_SIZE = 4
EPOCHS = 8
LR = 3e-5
MAX_LENGTH = 512

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"

SCHEMA = (
    "tables:\n"
    "CREATE TABLE bank_customer (id BIGINT, customer_no VARCHAR(20), name VARCHAR(50), id_card VARCHAR(18), phone VARCHAR(15), risk_level VARCHAR(10), status INT); "
    "CREATE TABLE bank_account (id BIGINT, account_no VARCHAR(30), customer_id BIGINT, account_type VARCHAR(20), balance DECIMAL(18,2), currency VARCHAR(5), open_date DATE, status INT); "
    "CREATE TABLE bank_transaction (id BIGINT, txn_no VARCHAR(40), account_id BIGINT, txn_type VARCHAR(20), amount DECIMAL(18,2), counterparty_account VARCHAR(30), remark VARCHAR(100), txn_time DATETIME)\n"
)

# ===== Load Data =====
print("Step 1: Loading dataset")
with open(DATASET_PATH, "r", encoding="utf-8") as f:
    raw = json.load(f)
split = int(len(raw) * 0.8)
train_raw, test_raw = raw[:split], raw[split:]
print(f"  Train: {len(train_raw)}, Test: {len(test_raw)}")

# ===== Load Model =====
print("Step 2: Loading model")
tok = AutoTokenizer.from_pretrained(TOKENIZER_NAME)
if tok.pad_token is None:
    tok.pad_token = tok.eos_token
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_NAME)
print(f"  Params: {sum(p.numel() for p in model.parameters()):,}")

# ===== Tokenize =====
def collate_fn(batch):
    qs = [SCHEMA + "query for: " + b["question"] for b in batch]
    sq = [b["query"] for b in batch]
    inp = tok(qs, max_length=MAX_LENGTH, truncation=True, padding=True, return_tensors="pt")
    out = tok(sq, max_length=MAX_LENGTH, truncation=True, padding=True, return_tensors="pt")
    out[out == tok.pad_token_id] = -100
    return {"input_ids": inp["input_ids"], "attention_mask": inp["attention_mask"], "labels": out["input_ids"]}

# ===== Train =====
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

# ===== Test =====
print("\nStep 4: Quick test")
model.eval()
for i in range(min(3, len(test_raw))):
    inp = tok(SCHEMA + "query for: " + test_raw[i]["question"], return_tensors="pt",
              truncation=True, max_length=MAX_LENGTH).to(device)
    with torch.no_grad():
        out = model.generate(**inp, max_length=MAX_LENGTH)
    pred = tok.decode(out[0], skip_special_tokens=True)
    match = "MATCH" if pred.strip().lower() == test_raw[i]["query"].strip().lower() else "DIFF"
    print(f"  Q: {test_raw[i]['question']}")
    print(f"  Expected: {test_raw[i]['query']}")
    print(f"  Pred: {pred}")
    print(f"  [{match}]")

print(f"\nDone! Model: {OUTPUT_DIR}")
