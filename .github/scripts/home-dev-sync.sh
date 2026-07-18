#!/usr/bin/env bash

set -euo pipefail

DOPPLER=/opt/chaeso-dev/bin/doppler
cd "$(dirname "$0")/../../deploy"

if [ -f /opt/chaeso-dev/doppler.env ]; then
  set -a; . /opt/chaeso-dev/doppler.env; set +a
  if "$DOPPLER" secrets download --no-file --format docker >.env.new 2>/dev/null; then
    mv .env.new .env
  else
    rm -f .env.new
    echo "⚠️ doppler 다운로드 실패 → 기존 .env 재사용" >&2
  fi
else
  echo "⚠️ doppler.env 없음 → 기존 .env 사용(초기 세팅 필요)" >&2
fi

docker compose -f docker-compose.home-dev.yml pull
docker compose -f docker-compose.home-dev.yml up -d
docker image prune -f
docker compose -f docker-compose.home-dev.yml ps
