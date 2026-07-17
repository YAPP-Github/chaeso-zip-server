#!/usr/bin/env bash
set -uo pipefail
cd /opt/app

env_value() {
  sudo grep "^$1=" .env | head -n1 | cut -d= -f2-
}

mgmt_port=$(env_value MANAGEMENT_PORT)
db_user=$(env_value DB_USER)
image_repo=$(env_value APP_IMAGE_REPO)
image_tag=$(env_value IMAGE_TAG)
compose_profiles=$(env_value COMPOSE_PROFILES)
services=$(sudo docker compose ps --services --status running)
fail=0

pass() { echo "✅ $1"; }
bad()  { echo "❌ $1"; fail=$((fail + 1)); }
skip() { echo "⏭  $1 (미기동 프로필)"; }
profile_enabled() {
  case ",${compose_profiles}," in
    *",$1,"*) return 0 ;;
    *) return 1 ;;
  esac
}

for key in APP_IMAGE_REPO IMAGE_TAG APP_PORT MANAGEMENT_PORT DB_HOST DB_NAME DB_USER REDIS_HOST REDIS_PORT COMPOSE_PROFILES; do
  if [ -n "$(env_value "$key")" ]; then
    pass "필수 환경값 존재($key)"
  else
    bad "필수 환경값 존재($key)"
  fi
done

echo "$services" | grep -qx app && pass "app 컨테이너 기동" || bad "app 컨테이너 기동"

curl -fsS -m 5 "http://localhost:${mgmt_port}/actuator/health" | grep -q '"status":"UP"' \
  && pass "app actuator health" || bad "app actuator health"

public_http_code=$(curl -sS -o /dev/null -w '%{http_code}' -m 5 "http://localhost:80/api/v1/samples" || true)
public_http_code=${public_http_code:-000}
if [ "$public_http_code" -ge 200 ] 2>/dev/null && [ "$public_http_code" -lt 500 ]; then
  pass "외부 HTTP 포트 라우팅(${public_http_code})"
else
  bad "외부 HTTP 포트 라우팅(${public_http_code})"
fi

app_container=$(sudo docker compose ps -q app)
running_image=$(sudo docker inspect -f '{{.Config.Image}}' "$app_container" 2>/dev/null || true)
expected_image="${image_repo}:${image_tag}"
[ "$running_image" = "$expected_image" ] \
  && pass "이미지 태그 일치(${image_tag})" || bad "이미지 태그 일치"

if echo "$services" | grep -qx db; then
  sudo docker compose exec -T db pg_isready -U "$db_user" </dev/null >/dev/null 2>&1 \
    && pass "postgres 응답(pg_isready)" || bad "postgres 응답(pg_isready)"
else
  profile_enabled embedded && bad "postgres 컨테이너 기동" || skip "postgres"
fi

if echo "$services" | grep -qx redis; then
  [ "$(sudo docker compose exec -T redis redis-cli ping </dev/null | tr -d '\r')" = "PONG" ] \
    && pass "redis 응답(ping)" || bad "redis 응답(ping)"
else
  profile_enabled embedded && bad "redis 컨테이너 기동" || skip "redis"
fi

if echo "$services" | grep -qx alloy; then
  pass "alloy(모니터링) 컨테이너 기동"
else
  profile_enabled monitoring && bad "alloy(모니터링) 컨테이너 기동" || skip "alloy"
fi

if [ -d /data ]; then
  mountpoint -q /data && pass "데이터 볼륨 마운트(/data)" || bad "데이터 볼륨 마운트(/data)"
  data_usage=$(df -P /data | awk 'NR == 2 { gsub(/%/, "", $5); print $5 }')
  if [ -n "$data_usage" ] && [ "$data_usage" -lt 85 ]; then
    pass "디스크 사용률 정상(/data ${data_usage}%)"
  else
    bad "디스크 사용률 정상(/data ${data_usage:-unknown}%)"
  fi
fi

echo "──────────────"
[ "$fail" -eq 0 ] && echo "전체 정상" || echo "$fail개 항목 실패"
exit "$fail"
