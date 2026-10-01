#!/usr/bin/env python3
"""실사 이미지 ZIP 검증기 + 매칭표(manifest.json) 생성기.

받은 ZIP(또는 폴더)의 파일명이 <key>_<A|B|C>.<png|jpg|jpeg|webp> 규칙에 맞는지, 20개 대상 상품에 모두 대응하는지,
정사각형·해상도·용량 기준을 지키는지 확인하고, 이미지↔상품 매칭표를 만든다.

사용
  python3 scripts/demo-products/photoreal_check.py photoreal.zip            # 검증 + manifest.json 출력
  python3 scripts/demo-products/photoreal_check.py ./photoreal_folder
옵션
  --min-side 1600   짧은 변 최소 픽셀(기본 1600)
  --out manifest.json   매칭표 저장 경로(기본: 입력 옆 manifest.json)
종료 코드: 대표(A)가 빠졌거나 규칙 위반이 있으면 1.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import tempfile
import zipfile

from PIL import Image

HERE = os.path.dirname(__file__)
TEMPLATE = os.path.join(HERE, "..", "..", "docs", "demo-products", "photoreal", "manifest.template.json")
NAME = re.compile(r"^(p\d{2})_([ABC])\.(png|jpg|jpeg|webp)$", re.IGNORECASE)


def collect(path):
    """파일명 → 실제 경로. ZIP 이면 임시 폴더에 푼다(하위 폴더 안의 파일도 이름만 본다)."""
    files = {}
    root = path
    if zipfile.is_zipfile(path):
        root = tempfile.mkdtemp(prefix="photoreal-")
        with zipfile.ZipFile(path) as archive:
            archive.extractall(root)
    for folder, _dirs, names in os.walk(root):
        if "__MACOSX" in folder:
            continue
        for name in names:
            if name.startswith("."):
                continue
            files.setdefault(name, os.path.join(folder, name))
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source")
    parser.add_argument("--min-side", type=int, default=1600)
    parser.add_argument("--out")
    args = parser.parse_args()

    template = json.load(open(TEMPLATE, encoding="utf-8"))
    targets = {item["key"]: item for item in template["items"]}
    files = collect(args.source)
    problems, notes = [], []
    matched = {}

    for name, path in sorted(files.items()):
        if name.lower() == "manifest.csv" or name.lower().endswith(".json"):
            continue
        m = NAME.match(name)
        if not m:
            problems.append(f"이름 규칙 위반: {name}  (예: p31_A.png)")
            continue
        key, cut = m.group(1).lower(), m.group(2).upper()
        if key not in targets:
            problems.append(f"대상 아닌 상품 키: {name}  (대상 {len(targets)}개: {', '.join(targets)})")
            continue
        try:
            with Image.open(path) as image:
                width, height = image.size
        except Exception as error:  # noqa: BLE001
            problems.append(f"열 수 없는 이미지: {name} ({error})")
            continue
        size = os.path.getsize(path)
        if abs(width - height) > max(width, height) * 0.02:
            problems.append(f"정사각형이 아님: {name} {width}x{height}")
        if min(width, height) < args.min_side:
            notes.append(f"해상도 낮음(권장 {args.min_side}px 이상): {name} {width}x{height}")
        if size > 10 * 1024 * 1024:
            problems.append(f"10MB 초과: {name} {size / 1048576:.1f}MB")
        sha = hashlib.sha256(open(path, "rb").read()).hexdigest()
        matched.setdefault(key, {})[cut] = {"file": name, "width": width, "height": height, "bytes": size, "sha256": sha}

    items, missing_a, missing_b = [], [], []
    for key, target in targets.items():
        got = matched.get(key, {})
        if "A" not in got:
            missing_a.append(key)
        if "B" not in got:
            missing_b.append(key)
        items.append({"key": key, "match": target["match"], "dbProductId": None,
                      "images": {cut: got[cut] for cut in "ABC" if cut in got}})

    count = sum(len(v) for v in matched.values())
    print(f"받은 이미지 {count}장 / 대상 상품 {len(targets)}개")
    print(f"  대표(A): {len(targets) - len(missing_a)}/{len(targets)}   소재 확대(B): {len(targets) - len(missing_b)}/{len(targets)}   "
          f"장면(C): {sum(1 for v in matched.values() if 'C' in v)}/{len(targets)}")
    if missing_a:
        problems.append("대표(A)가 없는 상품: " + ", ".join(missing_a))
    if missing_b:
        notes.append("소재 확대(B)가 없는 상품(선택): " + ", ".join(missing_b))
    for line in problems:
        print("  [오류]", line)
    for line in notes:
        print("  [참고]", line)

    out = args.out or os.path.join(os.path.dirname(os.path.abspath(args.source)), "manifest.json")
    json.dump({"version": 1, "items": items}, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"매칭표 저장: {out}")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
