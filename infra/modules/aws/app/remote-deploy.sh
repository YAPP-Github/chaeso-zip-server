#!/usr/bin/env bash
set -euo pipefail

export HOME="${HOME:-/root}"
umask 077

sha="${1:?usage: deploy.sh <git-sha>}"
cd /opt/app
touch .env

cert_domain=api.chaeso-zip.com

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
      echo "doppler 다운로드 실패, 캐시된 secrets.env 사용" >&2
    fi
    mkdir -p /opt/app/certs
    if doppler secrets get CF_ORIGIN_CERT --plain | base64 -d >/opt/app/certs/origin.pem.new &&
      doppler secrets get CF_ORIGIN_KEY --plain | base64 -d >/opt/app/certs/origin.key.new &&
      doppler secrets get CF_AOP_CA --plain | base64 -d >/opt/app/certs/aop-ca.pem.new &&
      [ -s /opt/app/certs/origin.pem.new ] && [ -s /opt/app/certs/origin.key.new ] &&
      [ -s /opt/app/certs/aop-ca.pem.new ]; then
      mv /opt/app/certs/origin.pem.new /opt/app/certs/origin.pem
      mv /opt/app/certs/origin.key.new /opt/app/certs/origin.key
      mv /opt/app/certs/aop-ca.pem.new /opt/app/certs/aop-ca.pem
      chmod 600 /opt/app/certs/origin.key
    else
      rm -f /opt/app/certs/origin.pem.new /opt/app/certs/origin.key.new /opt/app/certs/aop-ca.pem.new
      echo "인증서 다운로드 실패, 디스크의 캐시된 인증서 사용" >&2
    fi
  fi
  : >.env
  if [ -f /opt/app/secrets.env ]; then
    grep -vE '^CF_(ORIGIN|AOP)_' /opt/app/secrets.env >>.env || true
  fi
  echo "IMAGE_TAG=$1" >>.env
  [ -f /opt/app/infra.env ] && cat /opt/app/infra.env >>.env || true
}

validate_caddy() {
  docker compose run --rm --no-deps -T caddy caddy validate --config /etc/caddy/Caddyfile || return 1
  if ! openssl x509 -in /opt/app/certs/origin.pem -noout -checkend 604800 >/dev/null 2>&1; then
    echo "origin.pem 만료됨 또는 7일 내 만료 예정" >&2
    return 1
  fi
  if ! openssl x509 -in /opt/app/certs/origin.pem -noout -checkhost "$cert_domain" >/dev/null 2>&1; then
    echo "origin.pem SAN에 ${cert_domain} 없음 (엣지 인증서를 넣었는지 확인)" >&2
    return 1
  fi

  if ! openssl x509 -in /opt/app/certs/aop-ca.pem -noout -checkend 604800 >/dev/null 2>&1; then
    echo "aop-ca.pem 만료됨 또는 7일 내 만료 예정" >&2
    return 1
  fi
  if ! openssl x509 -in /opt/app/certs/aop-ca.pem -noout -ext basicConstraints 2>/dev/null | grep -q 'CA:TRUE'; then
    echo "aop-ca.pem이 CA 인증서가 아님 (CF에 올린 leaf를 넣었는지 확인)" >&2
    return 1
  fi
}
deploy() { docker compose pull && docker compose up -d && docker compose up -d --force-recreate caddy; }
healthy() {
  local mgmt_port cid
  mgmt_port=$(grep '^MANAGEMENT_PORT=' .env | cut -d= -f2)
  for _ in $(seq 1 30); do
    cid=$(docker compose ps -aq caddy 2>/dev/null || true)
    if curl -fsS -m 5 "http://localhost:${mgmt_port}/actuator/health" >/dev/null 2>&1 &&
      [ -n "$cid" ] &&
      [ "$(docker inspect -f '{{.State.Running}}{{.State.Restarting}}' "$cid" 2>/dev/null)" = truefalse ]; then
      return 0
    fi
    sleep 5
  done
  return 1
}

render_env "$sha"
if ! validate_caddy; then
  echo "Caddyfile/인증서 검증 실패, 배포 중단. 기존 컨테이너 유지" >&2
  exit 1
fi
deploy
if healthy; then
  docker image prune -f || true
  echo "deployed $sha"
  exit 0
fi

echo "health 실패, $prev_tag 로 롤백"
render_env "$prev_tag"
deploy
docker image prune -f || true
if healthy; then
  echo "롤백 완료: $prev_tag"
else
  echo "롤백 후에도 health 실패, 수동 점검 필요" >&2
fi
exit 1
