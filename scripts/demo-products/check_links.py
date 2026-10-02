#!/usr/bin/env python3
"""시연 이미지 링크 점검기 — 시드 SQL(V16·V17)이 쓰는 모든 이미지 주소가 열리는지 확인한다.

사용
  python3 scripts/demo-products/check_links.py                    # develop 기준으로 실제 확인
  python3 scripts/demo-products/check_links.py --ref feat/demo-curated-products   # 머지 전 브랜치 기준
  python3 scripts/demo-products/check_links.py --simulate-404     # 전부 404 라고 가정한 가상 시험(네트워크 없음)

점검 기준: 응답 200 이고 Content-Type 이 image/webp 여야 한다. 하나라도 어긋나면 종료 코드 1.
"""
import argparse
import concurrent.futures
import os
import re
import sys
import urllib.error
import urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
MIGRATIONS = os.path.join(ROOT, "src", "main", "resources", "db", "migration")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/"


def collect(ref):
    """V16(소분류 56장), V17(큐레이션), V19(시드 변형), V20(실사)가 가리키는 주소를 모은다."""
    urls = set()
    v16 = open(os.path.join(MIGRATIONS, "V16__demo_consumer_seed_images_gift_themes_artisans.sql"), encoding="utf-8").read()
    if "docs/seed-images/sub-" in v16:
        urls |= {f"{RAW}{ref}/docs/seed-images/sub-{n:02d}.webp" for n in range(1, 57)}
    for name in ("V17__demo_curated_products.sql", "V19__demo_seed_product_image_variants.sql", "V20__demo_photoreal_images.sql"):
        path = os.path.join(MIGRATIONS, name)
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        for match in re.finditer(r"https://raw\.githubusercontent\.com/Jangingmall/backend/develop/(docs/[A-Za-z0-9_./-]+\.webp)", text):
            urls.add(f"{RAW}{ref}/{match.group(1)}")
    return sorted(urls)


def fetch(url):
    try:
        request = urllib.request.Request(url, method="GET", headers={"User-Agent": "demo-link-check"})
        with urllib.request.urlopen(request, timeout=20) as response:
            return url, response.status, response.headers.get("Content-Type", "")
    except urllib.error.HTTPError as error:
        return url, error.code, ""
    except Exception as error:  # noqa: BLE001 - 네트워크 오류도 실패로 센다
        return url, 0, str(error)[:60]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ref", default="develop", help="확인할 브랜치 이름(기본 develop)")
    parser.add_argument("--simulate-404", action="store_true", help="모든 주소가 404 라고 가정한다(네트워크를 쓰지 않는다)")
    args = parser.parse_args()

    urls = collect(args.ref)
    if args.simulate_404:
        results = [(url, 404, "") for url in urls]
    else:
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(fetch, urls))

    bad = [(url, code, kind) for url, code, kind in results if code != 200 or not kind.startswith("image/webp")]
    print(f"점검 대상 {len(urls)}개 ({args.ref}{' · 404 가정' if args.simulate_404 else ''}): 정상 {len(urls) - len(bad)}개, 이상 {len(bad)}개")
    groups = {}
    for url, code, _ in bad:
        folder = url.split("/docs/", 1)[1].rsplit("/", 1)[0]
        groups.setdefault((code, folder), []).append(url)
    for (code, folder), items in sorted(groups.items()):
        print(f"  HTTP {code or '연결실패'}  docs/{folder}/  {len(items)}개")
    if bad:
        print("→ 머지 전이면 --ref 로 브랜치 이름을 주어 다시 확인하세요. 머지 후에도 이상이면 BE 담당에게 알려 주세요.")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
