#!/usr/bin/env bash
#
# gen-pr.sh
#
# 현재 Git workspace 변경사항을 기준으로 PR 제목/설명/checks를 자동 생성한다.
#
# 기본 동작:
#   - base = origin/develop 과 HEAD의 merge-base
#   - merge-base 이후 현재 작업트리까지의 변경을 계산
#   - staged / unstaged / committed 변경 포함
#   - untracked 파일 포함
#   - AI/Harness 설정 파일은 PR 산출물에서 제외
#
# 사용법:
#   ./scripts/gen-pr.sh
#   ./scripts/gen-pr.sh --watch
#   ./scripts/gen-pr.sh --out .context/pr-summary.md
#   ./scripts/gen-pr.sh --base develop
#   ./scripts/gen-pr.sh --heavy
#   ./scripts/gen-pr.sh --interval 2
#   ./scripts/gen-pr.sh --policy .ai/pr-policy.env
#
# 선택적 정책 파일:
#   .ai/pr-policy.env
#
# 예:
#   BASE_BRANCH="origin/develop"
#   PR_OUT=".context/pr-summary.md"
#   COMPILE_CMD="./gradlew -q compileJava"
#   TEST_CMD="./gradlew -q test"
#

set -euo pipefail

SCRIPT_NAME="$(basename "$0")"

# ============================================================
# 1. Git / Root
# ============================================================

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"

if [[ -z "$ROOT" ]]; then
  echo "[$SCRIPT_NAME] Git repository 내부에서 실행해야 합니다." >&2
  exit 1
fi

cd "$ROOT"

# macOS 터미널에서 한글/비ASCII 파일명을 깨지지 않게 표시
GIT=(git -c core.quotepath=false)

# ============================================================
# 2. 기본 정책
# ============================================================

BASE_BRANCH="${BASE_BRANCH:-origin/develop}"
PR_OUT="${PR_OUT:-.context/pr-summary.md}"

WATCH=0
HEAVY=0
INTERVAL=3

# 빌드/테스트 기본 명령
COMPILE_CMD="${COMPILE_CMD:-./gradlew -q compileJava}"
TEST_CMD="${TEST_CMD:-./gradlew -q test}"

# PR 파일 표시 개수
MAX_FILES="${MAX_FILES:-25}"

# Controller 테스트 검사 활성화
CONTROLLER_TEST_CHECK="${CONTROLLER_TEST_CHECK:-true}"

# AI/Harness 관련 파일
DEFAULT_EXCLUDES=(
  "CLAUDE.md"
  "AGENTS.md"
  "GEMINI.md"
  ".context"
  ".context/**"
  ".conductor"
  ".conductor/**"
  "opencode.json"
)

# 프로젝트별 정책 파일
POLICY_FILE=".ai/pr-policy.env"

# ============================================================
# 3. Utility
# ============================================================

log() {
  printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

die() {
  echo "[$SCRIPT_NAME] $*" >&2
  exit 1
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

# ============================================================
# 4. Argument parsing
# ============================================================

while [[ $# -gt 0 ]]; do
  case "$1" in
    --watch)
      WATCH=1
      ;;

    --heavy)
      HEAVY=1
      ;;

    --out)
      [[ $# -ge 2 ]] || die "--out 인자가 필요합니다."
      PR_OUT="$2"
      shift
      ;;

    --base)
      [[ $# -ge 2 ]] || die "--base 인자가 필요합니다."
      BASE_BRANCH="$2"
      shift
      ;;

    --interval)
      [[ $# -ge 2 ]] || die "--interval 인자가 필요합니다."
      INTERVAL="$2"
      shift
      ;;

    --policy)
      [[ $# -ge 2 ]] || die "--policy 인자가 필요합니다."
      POLICY_FILE="$2"
      shift
      ;;

    --help|-h)
      cat <<EOF
사용법:
  ./$SCRIPT_NAME
  ./$SCRIPT_NAME --watch
  ./$SCRIPT_NAME --heavy
  ./$SCRIPT_NAME --out FILE
  ./$SCRIPT_NAME --base BRANCH
  ./$SCRIPT_NAME --interval SEC
  ./$SCRIPT_NAME --policy FILE

옵션:
  --watch             변경 감지 후 PR summary 자동 갱신
  --heavy             compileJava + test 실행
  --out FILE          출력 파일
  --base BRANCH       기준 브랜치
  --interval SEC      watch polling 주기
  --policy FILE       정책 파일
  --help              도움말
EOF
      exit 0
      ;;

    *)
      die "알 수 없는 인자: $1"
      ;;
  esac

  shift
done

# ============================================================
# 5. Policy load
# ============================================================

#
# 정책 파일은 존재할 경우 source한다.
#
# 예:
#
#   BASE_BRANCH="origin/develop"
#   PR_OUT=".context/pr-summary.md"
#   COMPILE_CMD="./gradlew -q compileJava"
#   TEST_CMD="./gradlew -q test"
#   MAX_FILES=30
#   CONTROLLER_TEST_CHECK=true
#
if [[ -f "$POLICY_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$POLICY_FILE"
fi

# 정책 파일에서 EXCLUDES 배열을 직접 지정하면 그것을 사용
if [[ -z "${EXCLUDES+x}" ]]; then
  EXCLUDES=("${DEFAULT_EXCLUDES[@]}")
fi

# ============================================================
# 6. Exclude handling
# ============================================================

#
# Git diff용 pathspec 생성
#
build_git_excludes() {
  GIT_EXCLUDES=()

  local pattern
  for pattern in "${EXCLUDES[@]}"; do
    GIT_EXCLUDES+=(":(exclude)$pattern")
  done
}

#
# Bash glob 기반 exclude 검사
#
is_excluded_path() {
  local path="$1"

  case "$path" in
    CLAUDE.md|AGENTS.md|GEMINI.md|opencode.json)
      return 0
      ;;

    */CLAUDE.md|*/AGENTS.md|*/GEMINI.md)
      return 0
      ;;

    .context|.context/*)
      return 0
      ;;

    .conductor|.conductor/*)
      return 0
      ;;

    *)
      return 1
      ;;
  esac
}

build_git_excludes

# ============================================================
# 7. Base commit
# ============================================================

BASE_COMMIT="$(
  "${GIT[@]}" merge-base "$BASE_BRANCH" HEAD 2>/dev/null || true
)"

if [[ -z "$BASE_COMMIT" ]]; then
  die "merge-base 계산 실패 — 기준 브랜치 '$BASE_BRANCH' 확인 필요"
fi

BRANCH="$("${GIT[@]}" branch --show-current)"

if [[ -z "$BRANCH" ]]; then
  BRANCH="DETACHED_HEAD"
fi

SHORT_BASE="$(printf '%s' "$BASE_COMMIT" | cut -c1-12)"

# ============================================================
# 8. Diff facts
# ============================================================

#
# merge-base → 현재 작업트리
#
# 여기에는:
#   - base 이후 commit
#   - staged
#   - unstaged
#
# 가 모두 포함된다.
#
get_tracked_files() {
  "${GIT[@]}" diff \
    --name-status \
    "$BASE_COMMIT" \
    -- \
    . \
    "${GIT_EXCLUDES[@]}" \
    2>/dev/null || true
}

#
# untracked 파일
#
get_untracked_files() {
  local path

  while IFS= read -r path; do
    [[ -z "$path" ]] && continue

    if ! is_excluded_path "$path"; then
      printf 'A\t%s\n' "$path"
    fi
  done < <(
    "${GIT[@]}" ls-files \
      --others \
      --exclude-standard \
      2>/dev/null || true
  )
}

TRACKED_FILES="$(get_tracked_files)"
UNTRACKED_FILES="$(get_untracked_files)"

FILES=""

if [[ -n "$TRACKED_FILES" ]]; then
  FILES="$TRACKED_FILES"
fi

if [[ -n "$UNTRACKED_FILES" ]]; then
  if [[ -n "$FILES" ]]; then
    FILES="${FILES}"$'\n'"${UNTRACKED_FILES}"
  else
    FILES="$UNTRACKED_FILES"
  fi
fi

# 빈 줄 정리
FILES="$(printf '%s\n' "$FILES" | sed '/^[[:space:]]*$/d')"

FILE_COUNT="$(
  printf '%s\n' "$FILES" |
    sed '/^[[:space:]]*$/d' |
    wc -l |
    tr -d ' '
)"

# ============================================================
# 9. Diff stat
# ============================================================

STAT="$(
  "${GIT[@]}" diff \
    --stat \
    "$BASE_COMMIT" \
    -- \
    . \
    "${GIT_EXCLUDES[@]}" \
    2>/dev/null |
    tail -1 |
    sed 's/[[:space:]]*$//' || true
)"

if [[ -z "$STAT" ]]; then
  STAT="변경 통계 없음"
fi

# ============================================================
# 10. Commit facts
# ============================================================

COMMITS_AHEAD="$(
  "${GIT[@]}" rev-list \
    --count \
    "$BASE_BRANCH..HEAD" \
    2>/dev/null ||
    echo 0
)"

COMMIT_MSGS="$(
  "${GIT[@]}" log \
    --format='- %s' \
    "$BASE_BRANCH..HEAD" \
    2>/dev/null || true
)"

UNCOMMITTED="$(
  "${GIT[@]}" status \
    --porcelain \
    2>/dev/null |
    sed '/^[[:space:]]*$/d' |
    wc -l |
    tr -d ' '
)"

# ============================================================
# 11. File helpers
# ============================================================

file_path_from_status() {
  local line="$1"

  #
  # 일반 A/M/D:
  #   A       path
  #
  # Rename:
  #   R100    old    new
  #
  # 여기서는 PR 요약용이므로 마지막 path를 사용
  #
  printf '%s\n' "$line" |
    awk '{
      if (NF >= 3) {
        print $NF
      } else {
        print $2
      }
    }'
}

file_status_from_line() {
  printf '%s\n' "$1" | awk '{print $1}'
}

# ============================================================
# 12. Domain classification
# ============================================================

#
# 프로젝트별 domain rules:
#
# .ai/domain-rules.tsv
#
# 형식:
#
# member    src/main/java/*/member/*
# payment   src/main/java/*/payment/*
# product   src/main/java/*/product/*
#
# 파일이 없으면 generic fallback을 사용한다.
#

DOMAIN_RULES_FILE="${DOMAIN_RULES_FILE:-.ai/domain-rules.tsv}"

domain_of() {
  local path="$1"

  # 프로젝트 정의 규칙 우선
  if [[ -f "$DOMAIN_RULES_FILE" ]]; then
    local domain pattern

    while IFS=$'\t' read -r domain pattern; do
      [[ -z "$domain" ]] && continue
      [[ "$domain" == \#* ]] && continue

      #
      # case pattern은 *가 /를 포함한 전체 path에도 매칭된다.
      #
      case "$path" in
        $pattern)
          printf '%s\n' "$domain"
          return 0
          ;;
      esac
    done < "$DOMAIN_RULES_FILE"
  fi

  # Generic fallback
  case "$path" in
    docs/*)
      echo "docs"
      ;;

    src/test/*)
      echo "test"
      ;;

    gradle/*)
      echo "build"
      ;;

    scripts/*)
      echo "tooling"
      ;;

    monitoring/*)
      echo "infra"
      ;;

    .github/*)
      echo "ci"
      ;;

    *.json|*.yml|*.yaml|*.toml|*.properties)
      echo "config"
      ;;

    src/main/java/*)
      echo "backend"
      ;;

    *)
      echo "etc"
      ;;
  esac
}

# ============================================================
# 13. Domain statistics
# ============================================================

DOMAIN_TMP="$(mktemp "${TMPDIR:-/tmp}/gen-pr-domain.XXXXXX")"
trap 'rm -f "$DOMAIN_TMP"' EXIT

if [[ -n "$FILES" ]]; then
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue

    file="$(file_path_from_status "$line")"
    [[ -z "$file" ]] && continue

    domain="$(domain_of "$file")"
    printf '%s\n' "$domain" >> "$DOMAIN_TMP"
  done <<< "$FILES"
fi

TOP_DOMAIN="$(
  sort "$DOMAIN_TMP" |
    uniq -c |
    sort -rn |
    awk 'NR == 1 { print $2 }'
)"

TOP_DOMAIN="${TOP_DOMAIN:-backend}"

# ============================================================
# 14. Change type detection
# ============================================================

TYPE="chore"

#
# 커밋 메시지 우선
#
COMMIT_TYPE="$(
  printf '%s\n' "$COMMIT_MSGS" |
    grep -oE '(feat|fix|refactor|docs|test|chore|perf|style|build|ci)(\([^)]*\))?:' |
    sed -E 's/\(.*\)//' |
    tr -d ':' |
    sort |
    uniq -c |
    sort -rn |
    head -1 |
    awk '{print $2}' ||
    true
)"

if [[ -n "$COMMIT_TYPE" ]]; then
  TYPE="$COMMIT_TYPE"
else
  if printf '%s\n' "$FILES" | grep -qE '^A[[:space:]].*src/main/'; then
    TYPE="feat"
  elif printf '%s\n' "$FILES" | grep -qE '^D[[:space:]].*src/main/'; then
    TYPE="refactor"
  elif printf '%s\n' "$FILES" | grep -qE '^M[[:space:]].*src/main/'; then
    TYPE="refactor"
  fi

  # docs only
  if [[ "$FILE_COUNT" -gt 0 ]] &&
     ! printf '%s\n' "$FILES" | grep -qE '[[:space:]]src/'; then
    TYPE="docs"
  fi

  # test only
  if [[ "$FILE_COUNT" -gt 0 ]] &&
     ! printf '%s\n' "$FILES" | grep -qE '[[:space:]]src/main/'; then
    if printf '%s\n' "$FILES" | grep -qE '[[:space:]]src/test/'; then
      TYPE="test"
    fi
  fi
fi

# ============================================================
# 15. Scope / Subject
# ============================================================

SCOPE="$TOP_DOMAIN"

if [[ "$TYPE" == "docs" ]]; then
  SCOPE=""
fi

SUBJECT="변경"

case "$TOP_DOMAIN" in
  docs)
    SUBJECT="문서 갱신"
    ;;

  test)
    SUBJECT="테스트 보완"
    ;;

  config)
    SUBJECT="설정 변경"
    ;;

  tooling)
    SUBJECT="개발 도구 변경"
    ;;

  ci)
    SUBJECT="CI/CD 변경"
    ;;

  infra)
    SUBJECT="인프라 설정 변경"
    ;;

  *)
    if printf '%s\n' "$FILES" | grep -qE '^D[[:space:]]'; then
      SUBJECT="${TOP_DOMAIN} 도메인 정리"
    elif printf '%s\n' "$FILES" | grep -qE '^A[[:space:]]'; then
      SUBJECT="${TOP_DOMAIN} 도메인 추가/변경"
    else
      SUBJECT="${TOP_DOMAIN} 도메인 변경"
    fi
    ;;
esac

TITLE="$TYPE"

if [[ -n "$SCOPE" ]]; then
  TITLE="${TITLE}(${SCOPE})"
fi

TITLE="${TITLE}: ${SUBJECT}"

# ============================================================
# 16. Controller checks
# ============================================================

controller_check() {
  local ok=0
  local warn=0
  local fail=0

  if [[ "$CONTROLLER_TEST_CHECK" != "true" ]]; then
    echo "  ⚠️  Controller 테스트 검사를 비활성화함"
    return 0
  fi

  local controllers=""
  local line
  local status
  local path
  local filename
  local base

  while IFS= read -r line; do
    [[ -z "$line" ]] && continue

    status="$(file_status_from_line "$line")"
    path="$(file_path_from_status "$line")"

    case "$status" in
      A|M|R*|C*)
        case "$path" in
          *Controller.java)
            controllers="${controllers}"$'\n'"$path"
            ;;
        esac
        ;;
    esac
  done <<< "$FILES"

  controllers="$(printf '%s\n' "$controllers" | sed '/^[[:space:]]*$/d')"

  if [[ -z "$controllers" ]]; then
    echo "  ✅ Controller 변경 없음 — REST Docs 검사 생략"
    return 0
  fi

  while IFS= read -r path; do
    [[ -z "$path" ]] && continue

    filename="$(basename "$path" .java)"

    if [[ -d "src/test" ]] &&
       grep -rlq "$filename" src/test 2>/dev/null; then
      echo "  ✅ Controller 테스트 존재: $filename"
      ok=$((ok + 1))
    else
      echo "  ⚠️  Controller 테스트 확인 필요: $filename"
      warn=$((warn + 1))
    fi
  done <<< "$controllers"

  echo "  → Controller ✅$ok / ⚠️$warn / ❌$fail"
}

# ============================================================
# 17. Checks
# ============================================================

checks() {
  local ok=0
  local warn=0
  local fail=0

  echo "### Checks"
  echo ""

  # ----------------------------------------------------------
  # C1. Merge 가능 여부
  # ----------------------------------------------------------

  if git merge-tree --write-tree "$BASE_BRANCH" HEAD >/dev/null 2>&1; then
    echo "  ✅ merge 가능: '$BASE_BRANCH' ↔ 현재 브랜치"
    ok=$((ok + 1))
  else
    echo "  ❌ merge 충돌 가능성 있음: '$BASE_BRANCH' ↔ 현재 브랜치"
    fail=$((fail + 1))
  fi

  # ----------------------------------------------------------
  # C2. 변경 파일
  # ----------------------------------------------------------

  if [[ "$FILE_COUNT" -gt 0 ]]; then
    echo "  ✅ 변경 파일 ${FILE_COUNT}개"
    ok=$((ok + 1))
  else
    echo "  ❌ PR에 포함할 변경 없음"
    fail=$((fail + 1))
  fi

  # ----------------------------------------------------------
  # C3. Controller
  # ----------------------------------------------------------

  controller_check

  # controller_check 결과는 summary만 출력
  # 전체 점수에는 별도로 반영하지 않는다.
  #

  # ----------------------------------------------------------
  # C4. Commit / working tree
  # ----------------------------------------------------------

  if [[ "$COMMITS_AHEAD" -gt 0 ]]; then
    echo "  ✅ base 이후 커밋 ${COMMITS_AHEAD}개"
    ok=$((ok + 1))
  fi

  if [[ "$UNCOMMITTED" -gt 0 ]]; then
    echo "  ⚠️  미커밋 변경 ${UNCOMMITTED}개"
    warn=$((warn + 1))
  elif [[ "$COMMITS_AHEAD" -eq 0 ]]; then
    echo "  ⚠️  base 이후 커밋 없음"
    warn=$((warn + 1))
  fi

  # ----------------------------------------------------------
  # C5. Build / Test
  # ----------------------------------------------------------

  if [[ "$HEAVY" -eq 1 ]]; then
    echo ""

    echo "  ▶ compile"
    if eval "$COMPILE_CMD"; then
      echo "  ✅ compile 통과"
      ok=$((ok + 1))
    else
      echo "  ❌ compile 실패"
      fail=$((fail + 1))
    fi

    echo ""

    echo "  ▶ test"
    if eval "$TEST_CMD"; then
      echo "  ✅ test 통과"
      ok=$((ok + 1))
    else
      echo "  ❌ test 실패"
      fail=$((fail + 1))
    fi
  else
    echo "  ⚠️  build/test 미실행 (--heavy 필요)"
    warn=$((warn + 1))
  fi

  echo ""
  echo "  → Summary: ✅$ok / ⚠️$warn / ❌$fail"
}

# ============================================================
# 18. File summary
# ============================================================

print_file_summary() {
  if [[ -z "$FILES" ]]; then
    echo "- 변경 파일 없음"
    return 0
  fi

  local total=0
  local line

  while IFS= read -r line; do
    [[ -z "$line" ]] && continue

    total=$((total + 1))

    if [[ "$total" -le "$MAX_FILES" ]]; then
      echo "- $line"
    fi
  done <<< "$FILES"

  if [[ "$total" -gt "$MAX_FILES" ]]; then
    echo "- … 외 $((total - MAX_FILES))개 파일"
  fi
}

# ============================================================
# 19. Domain summary
# ============================================================

print_domain_summary() {
  if [[ ! -s "$DOMAIN_TMP" ]]; then
    echo "- 변경 도메인 없음"
    return 0
  fi

  sort "$DOMAIN_TMP" |
    uniq -c |
    sort -rn |
    awk '{
      printf "- %s: %s개 파일\n", $2, $1
    }'
}

# ============================================================
# 20. Commit summary
# ============================================================

print_commit_summary() {
  if [[ "$COMMITS_AHEAD" -gt 0 ]]; then
    printf '%s\n' "$COMMIT_MSGS"
  else
    echo "- base 이후 커밋 없음"
  fi
}

# ============================================================
# 21. Fingerprint for watch
# ============================================================

fingerprint() {
  {
    #
    # tracked changes
    #
    "${GIT[@]}" diff \
      --name-status \
      "$BASE_COMMIT" \
      -- \
      . \
      "${GIT_EXCLUDES[@]}" \
      2>/dev/null || true

    #
    # staged changes를 명시적으로 포함
    #
    "${GIT[@]}" diff \
      --cached \
      --name-status \
      2>/dev/null || true

    #
    # untracked
    #
    "${GIT[@]}" ls-files \
      --others \
      --exclude-standard \
      2>/dev/null |
      while IFS= read -r path; do
        [[ -z "$path" ]] && continue

        if ! is_excluded_path "$path"; then
          printf 'A\t%s\n' "$path"
        fi
      done

    #
    # branch
    #
    printf 'BRANCH\t%s\n' "$BRANCH"

  } | shasum | awk '{print $1}'
}

# ============================================================
# 22. Generate
# ============================================================

generate() {
  local ts
  ts="$(date '+%Y-%m-%d %H:%M:%S')"

  mkdir -p "$(dirname "$PR_OUT")"

  {
    echo "# PR 요약"
    echo ""
    echo "_자동 생성: ${ts}_"
    echo ""

    echo "## PR Title"
    echo ""
    echo '```text'
    echo "$TITLE"
    echo '```'
    echo ""

    echo "## Overview"
    echo ""
    echo "- 기준 브랜치: \`$BASE_BRANCH\`"
    echo "- merge-base: \`$SHORT_BASE\`"
    echo "- 현재 브랜치: \`$BRANCH\`"
    echo "- 변경 파일: ${FILE_COUNT}개"
    echo "- diff stat: $STAT"
    echo ""

    echo "## 변경 도메인"
    echo ""
    print_domain_summary
    echo ""

    echo "## 주요 변경 파일"
    echo ""
    print_file_summary
    echo ""

    echo "## Checks"
    echo ""
    checks
    echo ""

    echo "## Commit 목록"
    echo ""
    print_commit_summary
    echo ""

    echo "## Working Tree"
    echo ""
    if [[ "$UNCOMMITTED" -gt 0 ]]; then
      echo "- 미커밋 변경: ${UNCOMMITTED}개"
    else
      echo "- 미커밋 변경 없음"
    fi

    echo ""
    echo "## Notes"
    echo ""
    echo "- Agent/Harness 설정 파일은 PR 요약에서 제외했습니다."
    echo "- Build/Test는 \`--heavy\` 실행 시 실제 검증합니다."
    echo "- 제목/설명의 의미적 요약은 AI 또는 수동으로 보완할 수 있습니다."

  } > "$PR_OUT"

  log "$TITLE → $PR_OUT"
}

# ============================================================
# 23. Main
# ============================================================

if [[ "$WATCH" -eq 1 ]]; then
  echo "▶ watch 시작"
  echo "  workspace : $ROOT"
  echo "  branch    : $BRANCH"
  echo "  base      : $BASE_BRANCH"
  echo "  output    : $PR_OUT"
  echo "  interval  : ${INTERVAL}s"
  echo "  종료      : Ctrl+C"
  echo ""

  PREV=""

  while true; do
    CURRENT="$(fingerprint)"

    if [[ "$CURRENT" != "$PREV" ]]; then
      generate
      PREV="$CURRENT"
    fi

    sleep "$INTERVAL"
  done
else
  generate

  echo ""
  echo "------------------------------"
  echo "PR Summary"
  echo "------------------------------"
  cat "$PR_OUT"
fi