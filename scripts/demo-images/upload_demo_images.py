#!/usr/bin/env python3
"""시연용 상품 사진을 백엔드 이미지 API로 올리고 공개 링크를 만든다 (임시 도구 — merge 전 제거).

백엔드의 정식 업로드 흐름을 그대로 쓴다. S3 자격 증명이 필요 없다.
  1) POST /api/images/presigned-url  (AGENT 토큰 + memberId) -> 320w/640w/1280w 업로드 주소
  2) 각 WebP 변형을 업로드 주소로 PUT
  3) POST /internal/images/verify    -> 업로드를 확정(consume)해 24시간 뒤 자동 삭제되지 않게 한다
  4) 공개 주소(viewUrl)를 3초 안에 내려받아 image/webp 인지 확인한다

같은 사진(화소가 같거나 거의 같은 사진)은 한 번만 올리고 링크를 재사용한다.

사용 예:
  python3 scripts/demo-images/upload_demo_images.py --src tmp-demo-images/hapjukseon-maehwa/source \
      --base-url https://<STG API 주소> --token <AGENT 토큰> --member-id <소유 회원 ID> --out manifest.json
  (--dry-run 이면 변환·중복 검사만 하고 네트워크를 쓰지 않는다)
환경변수 DEMO_API_BASE_URL / DEMO_AGENT_TOKEN / DEMO_MEMBER_ID 로도 줄 수 있다. 필요: pip install pillow
"""
import argparse
import glob
import hashlib
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request

from PIL import Image, ImageOps

VARIANT_WIDTHS = (320, 640, 1280)
MAX_VARIANT_BYTES = 10 * 1024 * 1024
FETCH_TIMEOUT_SECONDS = 3
SIMILAR_HASH_DISTANCE = 4  # 16x16 평균 해시에서 이 값 이하로 다르면 같은 사진으로 본다


def fail(message):
    print(f"[ERROR] {message}", file=sys.stderr)
    sys.exit(1)


def load_image(path):
    return ImageOps.exif_transpose(Image.open(path)).convert("RGB")


def pixel_sha256(image):
    return hashlib.sha256(image.tobytes() + str(image.size).encode()).hexdigest()


def average_hash(image):
    gray = image.convert("L").resize((16, 16))
    pixels = list(gray.tobytes())
    mean = sum(pixels) / len(pixels)
    return "".join("1" if value > mean else "0" for value in pixels)


def hash_distance(a, b):
    return sum(x != y for x, y in zip(a, b))


def find_sources(src):
    patterns = ("*.png", "*.jpg", "*.jpeg", "*.webp")
    files = sorted({f for p in patterns for f in glob.glob(os.path.join(src, p))})
    if not files:
        fail(f"{src} 에서 이미지를 찾지 못했습니다.")
    return files


def dedupe(files):
    """(고유 사진 목록, {중복 파일: 원본 파일}) 을 돌려준다."""
    unique, duplicates = [], {}
    for path in files:
        image = load_image(path)
        sha, ahash = pixel_sha256(image), average_hash(image)
        match = next((u for u in unique if u["sha"] == sha or hash_distance(u["ahash"], ahash) <= SIMILAR_HASH_DISTANCE), None)
        if match:
            duplicates[path] = match["path"]
        else:
            unique.append({"path": path, "image": image, "sha": sha, "ahash": ahash})
    return unique, duplicates


def make_variants(image):
    """320w/640w/1280w WebP 바이트. 원본보다 큰 폭으로 키우지 않는다."""
    variants = {}
    for width in VARIANT_WIDTHS:
        target = min(width, image.width)
        height = max(1, round(image.height * target / image.width))
        buffer = io.BytesIO()
        image.resize((target, height), Image.LANCZOS).save(buffer, "WEBP", quality=85, method=6)
        data = buffer.getvalue()
        if len(data) > MAX_VARIANT_BYTES:
            fail(f"{width}w 변형이 10MB를 넘습니다.")
        variants[f"{width}w"] = data
    return variants


def request(method, url, token=None, body=None, content_type="application/json", timeout=30):
    data = body if isinstance(body, (bytes, bytearray)) else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    if data is not None:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read()


def api_json(method, url, token, body):
    status, _, raw = request(method, url, token, body)
    try:
        payload = json.loads(raw.decode() or "{}")
    except json.JSONDecodeError:
        payload = {}
    if status >= 300:
        fail(f"{method} {url} -> HTTP {status} {raw[:300].decode(errors='replace')}")
    return payload.get("data", payload)


def public_check(url):
    """공개 주소가 3초 안에 image/webp 로 내려오는지 (CDN 반영 지연을 고려해 3번 시도)."""
    for _ in range(3):
        started = time.time()
        try:
            status, headers, body = request("GET", url, timeout=FETCH_TIMEOUT_SECONDS)
            elapsed = time.time() - started
            ok = status == 200 and headers.get("Content-Type", "").lower().startswith("image/webp") and len(body) > 0
            if ok and elapsed <= FETCH_TIMEOUT_SECONDS:
                return True, f"200 image/webp {len(body)}B {elapsed:.2f}s"
            last = f"HTTP {status} {headers.get('Content-Type')} {len(body)}B {elapsed:.2f}s"
        except Exception as error:  # noqa: BLE001 - 네트워크 오류는 모두 실패로 본다
            last = f"{type(error).__name__}: {error}"
        time.sleep(1)
    return False, last


def upload_one(base_url, token, member_id, name, image, variants):
    body = {
        "fileName": f"{name}.webp",
        "contentType": "image/webp",
        "purpose": "PRODUCT",
        "sourceWidth": image.width,
        "sourceHeight": image.height,
        "variants": [{"name": key, "sizeBytes": len(data)} for key, data in variants.items()],
        "memberId": member_id,
    }
    presigned = api_json("POST", f"{base_url}/api/images/presigned-url", token, body)
    image_id = presigned["imageId"]
    for item in presigned.get("uploads") or presigned.get("variants"):
        status, _, raw = request("PUT", item.get("presignedUrl") or item["uploadUrl"], body=variants[item["variant"]],
                                 content_type="image/webp", timeout=60)
        if status >= 300:
            fail(f"{name} {item['variant']} 업로드 실패 HTTP {status} {raw[:200].decode(errors='replace')}")
    verified = api_json("POST", f"{base_url}/internal/images/verify", token, {"imageId": image_id, "requesterId": member_id})
    if not (verified.get("exists") and verified.get("ownerMatched")):
        fail(f"{name} ({image_id}) 확정 실패: {verified}")
    urls = {item["variant"]: item["viewUrl"] for item in (presigned.get("uploads") or presigned.get("variants"))}
    return image_id, urls


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--src", required=True)
    parser.add_argument("--base-url", default=os.environ.get("DEMO_API_BASE_URL"))
    parser.add_argument("--token", default=os.environ.get("DEMO_AGENT_TOKEN"))
    parser.add_argument("--member-id", type=int, default=int(os.environ["DEMO_MEMBER_ID"]) if os.environ.get("DEMO_MEMBER_ID") else None)
    parser.add_argument("--out", default="manifest.json")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    files = find_sources(args.src)
    unique, duplicates = dedupe(files)
    print(f"원본 {len(files)}장 -> 고유 {len(unique)}장, 중복 {len(duplicates)}장")
    for dup, original in duplicates.items():
        print(f"  중복: {os.path.basename(dup)} == {os.path.basename(original)} (한 번만 업로드)")

    entries = []
    for item in unique:
        name = os.path.splitext(os.path.basename(item["path"]))[0]
        variants = make_variants(item["image"])
        entry = {"name": name, "sha256": item["sha"], "source": item["path"], "width": item["image"].width,
                 "height": item["image"].height, "variantBytes": {k: len(v) for k, v in variants.items()}}
        if not args.dry_run:
            if not (args.base_url and args.token and args.member_id):
                fail("--base-url, --token, --member-id 가 필요합니다 (또는 DEMO_* 환경변수).")
            image_id, urls = upload_one(args.base_url.rstrip("/"), args.token, args.member_id, name, item["image"], variants)
            entry.update({"imageId": image_id, "urls": urls})
            checks = {variant: public_check(url) for variant, url in urls.items()}
            entry["publicCheck"] = {variant: {"ok": ok, "detail": detail} for variant, (ok, detail) in checks.items()}
            print(f"  {name}: {image_id} " + ", ".join(f"{v}={'OK' if ok else 'FAIL'}" for v, (ok, _) in checks.items()))
        entries.append(entry)
    for dup, original in duplicates.items():
        base = next(e for e in entries if e["source"] == original)
        entries.append({"name": os.path.splitext(os.path.basename(dup))[0], "duplicateOf": base["name"],
                        "imageId": base.get("imageId"), "urls": base.get("urls")})

    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump({"images": entries}, handle, ensure_ascii=False, indent=2)
    print(f"매니페스트: {args.out}")
    if not args.dry_run:
        bad = [e["name"] for e in entries if "publicCheck" in e and not all(c["ok"] for c in e["publicCheck"].values())]
        if bad:
            fail(f"공개 주소 확인에 실패한 사진: {', '.join(bad)}")


if __name__ == "__main__":
    main()
