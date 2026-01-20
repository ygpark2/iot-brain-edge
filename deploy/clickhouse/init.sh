#!/usr/bin/env bash
set -euo pipefail

CH_URL="${CLICKHOUSE_HTTP_URL:-http://localhost:8123}"
CH_USER="${CLICKHOUSE_USER:-brain}"
CH_PASS="${CLICKHOUSE_PASSWORD:-brain}"

SCHEMA_DIR="${CLICKHOUSE_SCHEMA_DIR:-deploy/clickhouse/schema}"
SCHEMA_GLOB="${CLICKHOUSE_SCHEMA_GLOB:-*.sql}"

wait_for_clickhouse() {
  echo "[clickhouse-init] waiting for ClickHouse at $CH_URL ..."
  for _ in $(seq 1 60); do
    if curl -fsS "$CH_URL/ping" >/dev/null 2>&1; then
      echo "[clickhouse-init] ClickHouse is up."
      return 0
    fi
    sleep 1
  done
  echo "[clickhouse-init] ClickHouse did not become ready in 60s" >&2
  return 1
}

exec_stmt() {
  local stmt="$1"

  # trailing whitespace + trailing ';' 제거 (POST /에서는 ';' 때문에 multi-statement로 오해 가능)
  stmt="$(printf "%s" "$stmt" | sed -E 's/[[:space:]]*;[[:space:]]*$//' )"

  # 빈 문장 스킵
  if [[ -z "$(printf "%s" "$stmt" | tr -d '[:space:]')" ]]; then
    return 0
  fi

  local preview
  preview="$(printf "%s" "$stmt" | tr '\n' ' ' | head -c 140)"
  echo "[clickhouse-init] -> stmt: ${preview}..."

  # 실패 시 바디 출력
  local tmp http
  tmp="$(mktemp)"
  http="$(curl -sS -o "$tmp" -w "%{http_code}" \
    -u "${CH_USER}:${CH_PASS}" \
    -H "Content-Type: text/plain; charset=utf-8" \
    "${CH_URL}/" \
    --data-binary "${stmt}"$'\n' || true)"

  if [[ "$http" != "200" ]]; then
    echo "[clickhouse-init] ERROR: statement failed (HTTP $http)" >&2
    echo "[clickhouse-init] --- statement ---" >&2
    printf "%s\n" "$stmt" >&2
    echo "[clickhouse-init] --- response (first 200 lines) ---" >&2
    sed -n '1,200p' "$tmp" >&2
    rm -f "$tmp"
    return 1
  fi

  rm -f "$tmp"
}

apply_file() {
  local file="$1"
  echo "[clickhouse-init] executing file: $file"

  local stmt=""
  while IFS= read -r line || [[ -n "$line" ]]; do
    # CRLF 제거
    line="${line%$'\r'}"

    # 주석 라인 제거
    [[ "$line" =~ ^[[:space:]]*-- ]] && continue

    # 누적
    stmt+="$line"$'\n'

    # 세미콜론으로 문장 종료 판단 (줄 끝에 ; 있는 경우)
    if [[ "$line" =~ \;[[:space:]]*$ ]]; then
      exec_stmt "$stmt"
      stmt=""
    fi
  done < "$file"

  # 파일 끝에 세미콜론 없이 끝난 문장이 있으면 실행
  if [[ -n "$(printf "%s" "$stmt" | tr -d '[:space:]')" ]]; then
    exec_stmt "$stmt"
  fi
}

main() {
  wait_for_clickhouse

  if [[ ! -d "$SCHEMA_DIR" ]]; then
    echo "[clickhouse-init] schema dir not found: $SCHEMA_DIR" >&2
    exit 1
  fi

  shopt -s nullglob
  files=( "$SCHEMA_DIR"/$SCHEMA_GLOB )
  shopt -u nullglob

  if [[ ${#files[@]} -eq 0 ]]; then
    echo "[clickhouse-init] no schema files found: ${SCHEMA_DIR}/${SCHEMA_GLOB}" >&2
    exit 1
  fi

  LC_ALL=C IFS=$'\n' sorted=( $(printf '%s\n' "${files[@]}" | sort) )
  unset IFS

  echo "[clickhouse-init] applying schemas from ${SCHEMA_DIR}/${SCHEMA_GLOB}"
  for f in "${sorted[@]}"; do
    [[ -f "$f" ]] || continue
    echo "[clickhouse-init] -> applying: $f"
    apply_file "$f"
  done

  echo "[clickhouse-init] done."
}

main "$@"