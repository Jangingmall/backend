#!/usr/bin/env python3
"""Gemini 가 만든 '시트'(여러 상품·컷을 한 장에 격자로 배치한 이미지)를 상품·컷별 파일로 자른다.

시트 규칙(브리프 10번): 가로로 A·B·C 세 장, 세로로 상품 N줄. 타일 사이는 단색 여백(흰색)이어야 경계를 찾을 수 있다.
파일명은 <key>_<컷>.png 로 저장하므로 바로 photoreal_check.py 로 검증할 수 있다.

사용
  python3 scripts/demo-products/photoreal_slice.py sheet1.png --keys p31,p32,p33 --out sliced/
  python3 scripts/demo-products/photoreal_slice.py sheet.webp --keys p31,p32,p33,p06,p11 --per-row 5 --out sliced/   # 한 줄에 상품 5개
옵션
  --cuts ABC        한 상품의 컷 이름(기본 ABC)
  --per-row 1       한 줄에 상품 몇 개가 가로로 놓였는지(기본 1: 한 줄 = 상품 하나, 컷 3장)
  --square          타일을 가운데 기준 정사각형으로 자른다(세로로 긴 타일일 때)
경계를 못 찾으면(타일 수가 안 맞으면) 균등 분할로 대신 자르고 경고한다.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image


def runs(values, threshold, min_len):
    out, start = [], None
    for i, v in enumerate(values):
        if v > threshold and start is None:
            start = i
        elif v <= threshold and start is not None:
            if i - start >= min_len:
                out.append((start, i))
            start = None
    if start is not None and len(values) - start >= min_len:
        out.append((start, len(values)))
    return out


def find_grid(image, rows_expected, cols_expected):
    array = np.asarray(image.convert("RGB")).astype(int)
    background = array[2:8, 2:8].reshape(-1, 3).mean(axis=0)
    mask = np.abs(array - background).sum(axis=2) > 30
    height, width = mask.shape
    row_runs = runs(mask.sum(axis=1), width * 0.15, height // (rows_expected * 6))
    row_runs = [r for r in row_runs if r[1] - r[0] > height // (rows_expected * 3)]
    if len(row_runs) != rows_expected:
        return None
    grid = []
    for y0, y1 in row_runs:
        col_runs = runs(mask[y0:y1].sum(axis=0), (y1 - y0) * 0.6, width // (cols_expected * 8))
        if len(col_runs) != cols_expected:
            return None
        grid.append([(x0, y0, x1, y1) for x0, x1 in col_runs])
    return grid


def equal_grid(image, rows, cols):
    width, height = image.size
    return [[(c * width // cols, r * height // rows, (c + 1) * width // cols, (r + 1) * height // rows) for c in range(cols)]
            for r in range(rows)]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("sheet")
    parser.add_argument("--keys", required=True, help="상품 키를 위에서 아래, 왼쪽에서 오른쪽 순서로(쉼표)")
    parser.add_argument("--cuts", default="ABC")
    parser.add_argument("--per-row", type=int, default=1)
    parser.add_argument("--square", action="store_true")
    parser.add_argument("--out", default="sliced")
    args = parser.parse_args()

    keys = [k.strip() for k in args.keys.split(",") if k.strip()]
    if len(keys) % args.per_row:
        sys.exit(f"상품 수({len(keys)})가 한 줄 상품 수({args.per_row})로 나누어 떨어지지 않습니다.")
    rows, cuts = len(keys) // args.per_row, len(args.cuts)
    cols = args.per_row * cuts
    image = Image.open(args.sheet).convert("RGB")
    grid = find_grid(image, rows, cols)
    if grid is None:
        print(f"[경고] 타일 경계를 찾지 못해 {rows}행 × {cols}열 균등 분할로 자릅니다. 여백이 단색인지 확인하세요.")
        grid = equal_grid(image, rows, cols)

    os.makedirs(args.out, exist_ok=True)
    smallest = None
    for r, row in enumerate(grid):
        for c, (x0, y0, x1, y1) in enumerate(row):
            key = keys[r * args.per_row + c // cuts]
            cut = args.cuts[c % cuts]
            tile = image.crop((x0, y0, x1, y1))
            if args.square:
                side = min(tile.size)
                left, top = (tile.width - side) // 2, (tile.height - side) // 2
                tile = tile.crop((left, top, left + side, top + side))
            tile.save(os.path.join(args.out, f"{key}_{cut}.png"))
            smallest = tile.size if smallest is None else min(smallest, tile.size, key=lambda s: min(s))
    print(f"{rows * cols}장 저장 -> {args.out}  (가장 작은 타일 {smallest[0]}x{smallest[1]})")
    if min(smallest) < 1000:
        print("[참고] 타일 짧은 변이 1000px 미만입니다. 시트를 더 크게(상품 수를 줄여서) 다시 받으면 화질이 좋아집니다.")


if __name__ == "__main__":
    main()
