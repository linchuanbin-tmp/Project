"""Show all test cases side by side"""
import json, torch, pymysql, os
os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM

tok = AutoTokenizer.from_pretrained('./fine-tuned-t5-banking')
model = AutoModelForSeq2SeqLM.from_pretrained('./fine-tuned-t5-banking')
model.eval()
SCHEMA = (
    'tables:\n'
    'CREATE TABLE bank_customer (id BIGINT, customer_no VARCHAR(20), name VARCHAR(50), id_card VARCHAR(18), phone VARCHAR(15), risk_level VARCHAR(10), status INT); '
    'CREATE TABLE bank_account (id BIGINT, account_no VARCHAR(30), customer_id BIGINT, account_type VARCHAR(20), balance DECIMAL(18,2), currency VARCHAR(5), open_date DATE, status INT); '
    'CREATE TABLE bank_transaction (id BIGINT, txn_no VARCHAR(40), account_id BIGINT, txn_type VARCHAR(20), amount DECIMAL(18,2), counterparty_account VARCHAR(30), remark VARCHAR(100), txn_time DATETIME)\n'
)

with open('text2sql_dataset.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

conn = pymysql.connect(host='localhost', user='root', password='zzm20030718',
                       database='agent_platform', charset='utf8mb4')

def run(sql):
    try:
        with conn.cursor() as c:
            c.execute(sql)
            return tuple(sorted(c.fetchall()))
    except:
        return None

ok = 0
for i, item in enumerate(data):
    inp = tok(SCHEMA + 'query for: ' + item['question'],
              return_tensors='pt', truncation=True, max_length=256)
    with torch.no_grad():
        pred = tok.decode(model.generate(**inp, max_length=256)[0],
                          skip_special_tokens=True).strip()
    gold = item['query'].strip()
    gr = run(gold)
    pr = run(pred)
    match = gr == pr
    if match:
        ok += 1
    tag = 'MATCH' if match else ('ERR' if pr is None else 'DIFF')

    q = item['question']
    if len(q) > 50:
        q = q[:50] + '...'
    g = gold
    if len(g) > 85:
        g = g[:85] + '...'
    p = pred
    if len(p) > 85:
        p = p[:85] + '...'

    print('[{}] {} | {}'.format(i + 1, tag, q))
    print('     G: ' + g)
    print('     P: ' + p)
    print()

print('=' * 60)
print('RESULTS: {}/{} = {:.1f}%'.format(ok, len(data), ok / len(data) * 100))
conn.close()
