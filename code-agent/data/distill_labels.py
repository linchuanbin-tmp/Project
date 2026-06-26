"""
知识蒸馏：用 API 批量生成训练标签
================================
读取 text2sql_dataset.json，逐条调用推理服务生成 SQL，
保存为 distilled_dataset.json 供 train_simple.py 训练使用。

前提：infer_server.py 已在运行 (python infer_server.py)
"""

import json, os, sys, time

SOURCE = "text2sql_dataset.json"
OUTPUT = "distilled_dataset.json"
INFER_URL = "http://localhost:8090/infer"

# 检查推理服务
try:
    import requests
    r = requests.get("http://localhost:8090/health", timeout=3)
    if r.status_code != 200:
        print("❌ 推理服务未就绪，请先运行: python infer_server.py")
        sys.exit(1)
except Exception:
    print("❌ 无法连接推理服务 (localhost:8090)，请先运行: python infer_server.py")
    sys.exit(1)

# 加载源数据
base = os.path.dirname(os.path.abspath(__file__))
src = os.path.join(base, SOURCE)
out = os.path.join(base, OUTPUT)

with open(src, "r", encoding="utf-8") as f:
    data = json.load(f)
print(f"📋 加载 {len(data)} 条数据，开始蒸馏...\n")

distilled = []
ok = fail = 0
start = time.time()

for i, item in enumerate(data):
    q = item["question"]
    try:
        r = requests.post(INFER_URL, json={"question": q}, timeout=120)
        d = r.json()
        sql = d.get("sql", "")
        if sql:
            distilled.append({"db_id": item["db_id"], "question": q, "query": sql})
            ok += 1
            print(f"[{i+1:3d}/{len(data)}] ✅ {q[:40]}")
        else:
            distilled.append(item)
            fail += 1
            print(f"[{i+1:3d}/{len(data)}] ⚠️ 空结果，保留原始: {q[:40]}")
    except Exception as e:
        distilled.append(item)
        fail += 1
        print(f"[{i+1:3d}/{len(data)}] ❌ {e}: {q[:40]}")

elapsed = time.time() - start

with open(out, "w", encoding="utf-8") as f:
    json.dump(distilled, f, ensure_ascii=False, indent=2)

print(f"\n{'='*55}")
print(f"蒸馏完成: {ok}/{len(data)} 条新标签, {fail} 条保留原始")
print(f"耗时: {elapsed:.0f}s")
print(f"输出: {OUTPUT}")
print(f"\n下一步: python train_simple.py  # 将自动使用蒸馏数据")