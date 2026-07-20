#!/usr/bin/env bash

set -euo pipefail
umask 077

sha="${1:-dev}"
[[ "$sha" =~ ^([0-9a-f]{7,40}|dev)$ ]] || { echo "invalid ref: $sha"; exit 1; }

DOPPLER=/opt/chaeso-dev/bin/doppler
COMPOSE=(docker compose -f docker-compose.home-dev.yml)
cd "$(dirname "$0")/../../deploy"

prev_tag=$("${COMPOSE[@]}" ps -q app 2>/dev/null \
  | xargs -r docker inspect --format '{{.Config.Image}}' 2>/dev/null \
  | awk -F: '{print $NF}' || true)

render_env() {
  if [ -f /opt/chaeso-dev/doppler.env ]; then
    set -a
    . /opt/chaeso-dev/doppler.env
    set +a
    if "$DOPPLER" secrets download --no-file --format docker >secrets.env.new 2>/dev/null; then
      mv secrets.env.new secrets.env
    else
      rm -f secrets.env.new
      echo "⚠️ doppler 다운로드 실패 → 캐시된 secrets.env 재사용" >&2
    fi
  else
    echo "⚠️ doppler.env 없음 → 캐시된 secrets.env 사용(초기 세팅 필요)" >&2
  fi
  [ -f secrets.env ] || { echo "❌ secrets.env 없음 — 홈서버 초기 세팅 필요" >&2; exit 1; }
  : >.env
  cat secrets.env >>.env
  echo "IMAGE_TAG=$1" >>.env
}

deploy() { "${COMPOSE[@]}" pull && "${COMPOSE[@]}" up -d --remove-orphans; }

healthy() {
  for _ in $(seq 1 20); do
    if curl -fsS -m 3 http://127.0.0.1:8081/actuator/health >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
  done
  return 1
}

rollback() {
  if [ -z "$prev_tag" ] || [ "$prev_tag" = dev ] || [ "$prev_tag" = "$sha" ]; then
    echo "⚠️ 롤백 대상 없음(직전 실행 중이던 이미지 없음) — 홈서버 수동 점검 필요" >&2
    exit 1
  fi
  echo "롤백: $prev_tag" >&2
  render_env "$prev_tag"
  if deploy && healthy; then
    echo "롤백 성공 → $prev_tag 정상 복구" >&2
  else
    echo "⚠️ 롤백 후에도 health 실패 — 홈서버 수동 점검 필요" >&2
  fi
  docker image prune -f || true
  exit 1
}

render_env "$sha"

if ! deploy; then
  echo "❌ pull/up 실패" >&2
  rollback
fi

if ! healthy; then
  echo "----- app logs -----" >&2
  "${COMPOSE[@]}" logs --tail=120 app >&2 || true
  echo "❌ health 실패" >&2
  rollback
fi

docker image prune -f || true
echo "deployed $sha"
"${COMPOSE[@]}" ps
