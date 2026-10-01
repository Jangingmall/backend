#!/usr/bin/env python3
"""시드 상품 729개(대표 56개 제외 673개)마다 상품별 대표컷(A) 프롬프트를 만든다.

photoreal_all.py 의 T3(소분류×소재 변형 180장)를 상품 1개당 1장으로 늘린 안이다.
결과: docs/demo-products/photoreal/all/perproduct/{prompts.txt, manifest.json, master_pp_NN.txt}
실행: python3 scripts/demo-products/photoreal_perproduct.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import photoreal_all as A  # noqa: E402

OUT = os.path.join(A.OUT, "perproduct")


def main():
    os.makedirs(OUT, exist_ok=True)
    P = json.load(open(os.path.join(os.path.dirname(__file__), "seed_products.json"), encoding="utf-8"))
    for p in P:
        p["cls"] = A.V.classify(p["material"])
    flag = {}
    for p in sorted(P, key=lambda x: x["id"]):
        flag.setdefault(p["sub"], p)
    fids = {p["id"] for p in flag.values()}
    rows = [p for p in sorted(P, key=lambda x: x["id"]) if p["id"] not in fids]
    names = {s: n for s, n, _c, _k in A.V.seed.ITEMS}
    items = []
    for n, p in enumerate(rows, 1):
        prompt = (f"Product photo of {A.SUBNOUN[p['sub']]} made of {A.MAT[p['cls']]}. "
                  f"Korean product name for reference (ignore the maker's name, never write any text on the image): \"{p['title']}\", material: {p['material']}. "
                  f"Show one single item that fits this name, {A.A_TAIL}")
        items.append((n, f"d{p['id']:03d}_A", p, prompt))
    lines = ["# 상품별 대표컷 프롬프트 673개 — 한 줄이 이미지 한 장 (정사각형 1:1)", ""]
    manifest = []
    for n, key, p, prompt in items:
        lines.append(f"[{n:03d}] {key} ({names[p['sub']]} · {p['title']}) | {prompt}")
        manifest.append({"no": n, "key": key, "productId": p["id"], "sub": p["sub"], "cls": p["cls"], "title": p["title"], "prompt": prompt})
    open(os.path.join(OUT, "prompts.txt"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    head = open(os.path.join(A.OUT, "master_prompt_01.txt"), encoding="utf-8").read().split("번호 목록\n")[0]
    for k in range(0, len(items), 60):
        chunk = items[k:k + 60]
        h = head.replace("1~60번", f"{chunk[0][0]}~{chunk[-1][0]}번")
        h = h.replace("키가 _A 인 컷은", "모든 컷이 대표(A) 컷이야. 단색 따뜻한 연회색 배경, 소품·표면 질감 없음, 같은 배경 톤. 키가 _A 인 컷은")
        body = "\n\n".join(f"[{n:03d}] {key}\n{prompt}" for n, key, p, prompt in chunk)
        open(os.path.join(OUT, f"master_pp_{k // 60 + 1:02d}.txt"), "w", encoding="utf-8").write(
            h + "번호 목록\n" + body + "\n\n위 설명을 모두 이해했으면 아무것도 만들지 말고 '준비 완료'라고만 답해 줘.\n")
    print(len(items), "prompts", (len(items) + 59) // 60, "files")


if __name__ == "__main__":
    main()
