#!/usr/bin/env bash
set -euo pipefail

export HOME="${HOME:-/root}"
umask 077

sha="${1:?usage: deploy.sh <git-sha>}"
cd /opt/app
touch .env

prev_tag=$(grep '^IMAGE_TAG=' .env | cut -d= -f2 || echo latest)

render_env() {
  if [ -f /opt/app/doppler.env ]; then
    set -a
    . /opt/app/doppler.env
    set +a
    if doppler secrets download --no-file --format docker >/opt/app/secrets.env.new; then
      mv /opt/app/secrets.env.new /opt/app/secrets.env
    else
      rm -f /opt/app/secrets.env.new
      echo "⚠️ doppler 다운로드 실패 → 캐시된 secrets.env 재사용" >&2
    fi
  fi
  : >.env
  [ -f /opt/app/secrets.env ] && cat /opt/app/secrets.env >>.env
  echo "IMAGE_TAG=$1" >>.env
  [ -f /opt/app/infra.env ] && cat /opt/app/infra.env >>.env || true
}

deploy() { docker compose pull && docker compose up -d; }

healthy() {
  local mgmt_port
  mgmt_port=$(grep '^MANAGEMENT_PORT=' .env | cut -d= -f2)
  for _ in $(seq 1 30); do
    if curl -fsS -m 5 "http://localhost:${mgmt_port}/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  return 1
}

render_env "$sha"
deploy
if healthy; then
  docker image prune -f || true
  echo "deployed $sha"
  exit 0
fi

echo "health 실패 → 롤백: $prev_tag"
render_env "$prev_tag"
deploy
docker image prune -f || true
if healthy; then
  echo "롤백 성공 → $prev_tag 정상 복구 (서비스 유지)"
else
  echo "⚠️ 롤백 후에도 health 실패 — 박스 수동 점검 필요"
fi
exit 1
