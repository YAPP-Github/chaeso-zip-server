# dev(테스트) 환경 인프라 — 홈서버

프로덕션(AWS)은 [infra.md](./infra.md) 참고.

dev 브랜치 Merge시 자동 배포됩니다.

---

## 1. 구조

```
.github/workflows/deploy-home-dev.yml   # dev push → 이미지 빌드(GHCR) → Tailscale SSH 배포
.github/scripts/home-dev-sync.sh        # 홈서버에서 실행: .env 렌더 → 배포 → health → 실패 시 롤백
deploy/docker-compose.home-dev.yml      # dev 스택 (app / postgres / redis / mailpit)
```

## 2. 환경변수 추가시

- 새 환경 변수 추가가 필요할 경우 `application.yaml` 에 기본값을 두거나, Doppler dev 환경변수에 추가해주세요.
- 새로운 스택(도커 컨테이너)을 추가할 경우 compose 세 곳에 일괄 적용해야 합니다.

```
deploy/docker-compose.yml            # prod (AWS)
deploy/docker-compose.home-dev.yml   # dev (홈서버)
docker-compose.yml                   # local
```
