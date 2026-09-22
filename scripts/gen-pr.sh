#!/usr/bin/env bash
#
# gen-pr.sh — 현재 워크스페이스 변경에서 PR title / description을 자동 생성 + checks 표시
#
# 동작 방식 (Conductor와 동일한 기준)
#   - base = origin/develop  과 HEAD 의 merge-base
#   - 그 merge-base → 현재 작업트리 diff (커밋 전 변경 포함)
#   - 에이전트 설정 파일(CLAUDE.md / AGENTS.md / GEMINI.md 등)은 PR 산출물에서 제외
#
# 사용법
#   ./scripts/gen-pr.sh                 # 1회 생성 후 stdout 출력
#   ./scripts/gen-pr.sh --watch         # 변경 감지 시 실시간 재생성 (실시간 수정반영)
#   ./scripts/gen-pr.sh --out 파일.md    # 출력 파일 지정 (기본 .context/pr-summary.md)
#   ./scripts/gen-pr.sh --base develop  # 기준 브랜치 변경
#   ./scripts/gen-pr.sh --heavy         # gradle 컴파일/테스트 실제 실행 (느림)
#   ./scripts/gen-pr.sh --interval 2    # watch 폴링 간격(초), 기본 3
#
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

# 비ASCII 파일명을 quote 없이 UTF-8 그대로 다루기 위한 전역 git 옵션
GIT="git -c core.quotepath=false"

BASE="${BASE_BRANCH:-origin/develop}"
OUT="${PR_OUT:-.context/pr-summary.md}"
WATCH=0
HEAVY=0
INTERVAL=3

while [ $# -gt 0 ]; do
  case "$1" in
    --watch) WATCH=1 ;;
    --heavy) HEAVY=1 ;;
    --out) OUT="${2:?--out 인자 필요}"; shift ;;
    --base) BASE="${2:?--base 인자 필요}"; shift ;;
    --interval) INTERVAL="${2:?--interval 인자 필요}"; shift ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
  shift
done

# 에이전트 설정 파일 제외 패턴
EXCLUDE_AGENT_FILES='(exclude)CLAUDE.md (exclude)AGENTS.md (exclude)GEMINI.md (exclude)**/CLAUDE.md (exclude).context (exclude).conductor (exclude)opencode.json'

BASE_COMMIT="$(git merge-base "$BASE" HEAD 2>/dev/null || echo "")"
if [ -z "$BASE_COMMIT" ]; then
  echo "❌ merge-base 계산 실패 — 기준 브랜치 '$BASE' 확인 필요" >&2
  exit 1
fi

BRANCH="$($GIT branch --show-current)"

# ---------- diff facts ----------
# merge-base → 작업트리 전체 변경 (커밋 전 포함), 에이전트 설정 파일 제외
FILES="$($GIT diff --name-status "$BASE_COMMIT" -- . $EXCLUDE_AGENT_FILES 2>/dev/null || \
        $GIT diff --name-status "$BASE_COMMIT")"
# 신규(untracked) 파일도 PR 콘텐츠에 포함
if UNTRACKED="$($GIT status --porcelain | grep '^??' | sed 's/^?? //' | grep -vE '^\.context|^\.conductor|^opencode\.json$' || true)"; then
  if [ -n "$UNTRACKED" ]; then
    FILES="$(printf '%s\n%s\n' "$FILES" "$(printf '%s\n' "$UNTRACKED" | sed 's/^/A\t/')" | sed '/^$/d')"
  fi
fi
FILE_COUNT="$(printf '%s\n' "$FILES" | sed '/^$/d' | wc -l | tr -d ' ')"
STAT="$($GIT diff --stat "$BASE_COMMIT" 2>/dev/null | tail -1 || true)"
COMMITS_AHEAD="$($GIT log --oneline "$BASE"..HEAD 2>/dev/null | sed '/^$/d' | wc -l | tr -d ' ')"
COMMIT_MSGS="$($GIT log --format='- %s' "$BASE"..HEAD 2>/dev/null || true)"
UNCOMMITTED="$(git status --porcelain | sed '/^$/d' | wc -l | tr -d ' ')"

# 도메인 분류 (src/main/java 하위 도메인 + 기타)
domain_of() {
  local p="$1"
  case "$p" in
    docs/*) echo "docs" ;;
    src/test/*) echo "test" ;;
    gradle/*) echo "build" ;;
    scripts/*) echo "tooling" ;;
    *.json|*.yml|*.yaml|*.toml|*.properties) echo "config" ;;
    opencode.json) echo "config" ;;
    monitoring/*) echo "infra" ;;
    .github/*) echo "ci" ;;
    src/main/java/*/member/*|src/main/java/*/auth/*) echo "member" ;;
    src/main/java/*/payment/*) echo "payment" ;;
    src/main/java/*/admin/*) echo "admin" ;;
    src/main/java/*/image/*) echo "image" ;;
    src/main/java/*/product/*|src/main/java/*/order/*) echo "product" ;;
    src/main/java/*/content/*) echo "content" ;;
    src/main/java/*/notification/*) echo "notification" ;;
    src/main/java/*/chatbot/*) echo "chatbot" ;;
    src/main/java/*) echo "backend" ;;
    *) echo "etc" ;;
  esac
}

printf '%s\n' "$FILES" | sed '/^$/d' | awk '{print $2}' | while read -r f; do domain_of "$f"; done | sort | uniq -c | sort -rn > /tmp/genpr_domains.$$ 2>/dev/null || true

# 도메인 대표 선정 (가장 많이 변경된 항목)
TOP_DOMAIN=""
if [ -s /tmp/genpr_domains.$$ ]; then
  TOP_DOMAIN="$(awk '{print $2}' /tmp/genpr_domains.$$ | head -1)"
fi
rm -f /tmp/genpr_domains.$$

# ---------- type / scope / title 추론 ----------
TYPE="chore"
if printf '%s\n' "$FILES" | grep -q '^A.*src/main/' && printf '%s\n' "$FILES" | grep -q '^D'; then
  TYPE="refactor"
elif printf '%s\n' "$FILES" | grep -q '^A.*src/main/'; then
  TYPE="feat"
elif command -v grep >/dev/null; then
  # 커밋 메시지에 fix/feat 등의 타입이 있으면 우선
  CTYPE="$(printf '%s\n' "$COMMIT_MSGS" | grep -oE '^(fix|feat|refactor|docs|test|chore|perf|style):' | sort | uniq -c | sort -rn | head -1 | awk '{print $2}' | tr -d ':' || true)"
  [ -n "$CTYPE" ] && TYPE="$CTYPE"
fi
# docs-only / test-only 분류
if ! printf '%s\n' "$FILES" | grep -qE '\ssrc/'; then
  TYPE="docs"
elif printf '%s\n' "$FILES" | grep -qE '\ssrc/main/' 2>/dev/null; then
  :
fi

SCOPE="${TOP_DOMAIN:-backend}"
case "$TYPE" in docs) SCOPE="" ;; esac

# 제목 요약 후보 (도메인 기반 결정론적 기본값 — 마지막에 에이전트/사용자가 다듬을 수 있음)
SUBJECT=""
if [ "$TOP_DOMAIN" = "docs" ]; then
  SUBJECT="샘플 데이터·문서 갱신"
elif [ "$TOP_DOMAIN" = "test" ]; then
  SUBJECT="테스트 보완"
elif printf '%s\n' "$FILES" | grep -q '^D'; then
  SUBJECT="${TOP_DOMAIN} 도메인 정리 (삭제 포함)"
else
  SUBJECT="${TOP_DOMAIN} 도메인 변경"
fi
TITLE="$TYPE"
[ -n "$SCOPE" ] && TITLE="$TYPE($SCOPE)"
TITLE="$TITLE: $SUBJECT"

# ---------- checks ----------
checks() {
  local ok=0 warn=0 fail=0
  # C1: merge 가능 여부 (작업트리 변경 안 함)
  if git merge-tree --write-tree "$BASE" HEAD >/dev/null 2>&1; then
    echo "  ✅ merge 가능: '$BASE' ↔ 현재 브랜치 충돌 없음"; ok=$((ok+1))
  else
    echo "  ❌ merge 충돌: '$BASE'와 충돌 — 해결 필요"; fail=$((fail+1))
  fi

  # C2: PR에 포함될 변경 존재 여부
  if [ "$FILE_COUNT" -gt 0 ]; then
    echo "  ✅ 변경 파일 ${FILE_COUNT}개 (merge-base 기준, 에이전트 설정 파일 제외)"; ok=$((ok+1))
  else
    echo "  ❌ PR에 포함할 변경 없음 (에이전트 설정 파일만 변경됨)"; fail=$((fail+1))
  fi

  # C3: 신규/수정 Controller → REST Docs 테스트 존재 여부 (CLAUDE.md 규칙)
  local new_ctrl="$(printf '%s\n' "$FILES" | grep -E '^A.*src/main/.+Controller\.java$' | awk '{print $2}' || true)"
  if [ -n "$new_ctrl" ]; then
    while IFS= read -r c; do
      local base="$(basename "$c" .java)"
      if grep -rlq "$base" src/test 2>/dev/null; then
        echo "  ✅ REST Docs 테스트 존재: $base"; ok=$((ok+1))
      else
        echo "  ⚠️  REST Docs 테스트 없음: $base (신규 엔드포인트 = 테스트 필수)"; warn=$((warn+1))
      fi
    done <<< "$new_ctrl"
  else
    echo "  ✅ 신규 Controller 없음 — REST Docs 추가 테스트 불필요"; ok=$((ok+1))
  fi

  # C4: 커밋 상태
  if [ "$COMMITS_AHEAD" -gt 0 ]; then
    echo "  ✅ 브랜치 커밋 ${COMMITS_AHEAD}개 (base 이후)"; ok=$((ok+1))
  elif [ "$UNCOMMITTED" -gt 0 ]; then
    echo "  ⚠️  아직 커밋 전 상태 (작업트리 변경 ${UNCOMMITTED}개) — PR 전 커밋 권장"; warn=$((warn+1))
  else
    echo "  ✅ 변경 없음 (PR 불필요)"; ok=$((ok+1))
  fi

  # C5: 빌드/테스트 (--heavy 한정)
  if [ "$HEAVY" = "1" ]; then
    if ./gradlew -q compileJava >/dev/null 2>&1; then
      echo "  ✅ compileJava 통과"; ok=$((ok+1))
    else
      echo "  ❌ compileJava 실패"; fail=$((fail+1))
    fi
    if ./gradlew -q test >/dev/null 2>&1; then
      echo "  ✅ 전체 테스트 통과"; ok=$((ok+1))
    else
      echo "  ❌ 테스트 실패"; fail=$((fail+1))
    fi
  else
    echo "  ⚠️  빌드/테스트 미실행 (--heavy 로 실제 실행 가능)"; warn=$((warn+1))
  fi
  echo "  → ✅$ok / ⚠️$warn / ❌$fail"
}

# ---------- description 작성 ----------
generate() {
  local ts="$(date '+%Y-%m-%d %H:%M:%S')"
  mkdir -p "$(dirname "$OUT")"
  {
    echo "# PR 요약 (자동 생성 @ $ts)"
    echo ""
    echo "## 📌 PR Title"
    echo "\`\`\`"
    echo "$TITLE"
    echo "\`\`\`"
    echo ""
    echo "## 📋 PR Description"
    echo ""
    echo "### 개요"
    echo "- 변경 파일 ${FILE_COUNT}개 ($STAT)"
    echo "- 기준: \`$BASE\` (merge-base \`$(echo "$BASE_COMMIT" | cut -c1-12)\`) / 브랜치: \`$BRANCH\`"
    echo ""
    echo "### 주요 변경 (도메인별)"
    if [ -n "$FILES" ]; then
      printf '%s\n' "$FILES" | sed -n '1,25p' | sed 's/^/- /' | sed 's/\.java$/\.java/' || true
      local total="$(printf '%s\n' "$FILES" | sed '/^$/d' | wc -l | tr -d ' ')"
      [ "$total" -gt 25 ] && echo "- … 외 $((total-25))개 파일"
    else
      echo "- (에이전트 설정 파일 외 변경 없음)"
    fi
    echo ""
    echo "### 체크리스트 (Checks)"
    checks
    echo ""
    echo "### 커밋 목록"
    if [ "$COMMITS_AHEAD" -gt 0 ]; then
      echo "$COMMIT_MSGS"
    else
      echo "- 커밋 전 상태 — PR 전에 커밋 필요"
    fi
    echo ""
    echo "> 제목/설명을 다듬으려면 수동 편집하거나 에이전트에게 요청."
  } > "$OUT"
  echo "[$(date '+%H:%M:%S')] $TITLE → $OUT"
}

# ---------- watch (실시간 수정반영) ----------
fingerprint() {
  { git status --porcelain; $GIT diff "$BASE_COMMIT" -- . $EXCLUDE_AGENT_FILES 2>/dev/null || git diff "$BASE_COMMIT"; } | shasum | cut -d' ' -f1
}

if [ "$WATCH" = "1" ]; then
  echo "▶️  watch 시작: '$OUT' 실시간 갱신 (폴링 ${INTERVAL}s) — Ctrl+C 로 종료"
  PREV=""
  while true; do
    FP="$(fingerprint)"
    if [ "$FP" != "$PREV" ]; then
      generate >/dev/null
      tail -n 1 "$OUT" 2>/dev/null || true
      PREV="$FP"
    fi
    sleep "$INTERVAL"
  done
else
  generate
  echo ""
  echo "--- 산출물: $OUT (아래) ---"
  cat "$OUT"
fi