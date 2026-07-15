# 전체 목록은 make help
#
#   공통: status,ssh/health/check/ps/logs
#   운영: bootstrap, up, down, redeploy
#
# 전부 AWS_PROFILE 필요: export AWS_PROFILE=[프로파일 이름]

SHELL := bash

REGION          := ap-northeast-2
STATE_BUCKET    := chaeso-zip-tfstate
ENV_DIR         := infra/environments/aws/prod
CHECK_SCRIPT    := infra/check.sh
MANAGEMENT_PORT := 8081
INSTANCE_TAG    := chaeso-zip-vm

.DEFAULT_GOAL := help
.PHONY: help bootstrap up down status redeploy health check ps logs ssh _profile

# 접속 헬퍼
ssm_target = $$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=$(INSTANCE_TAG)" "Name=instance-state-name,Values=running" \
  --query "Reservations[0].Instances[0].InstanceId" --output text)

ssh_via_ssm = ssh -i ~/.ssh/chaeso-zip -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR \
  -o ProxyCommand="aws ssm start-session --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p"

ssh_to = $(ssh_via_ssm) ubuntu@$(ssm_target)

_profile:
	@: $${AWS_PROFILE:?미설정 — 'export AWS_PROFILE=jiny' 후 다시 실행}
	@echo "계정: $$(aws sts get-caller-identity --query Account --output text)"

help:
	@echo "공통 :"
	@echo "  make status     인스턴스 ID, 공인 IP, 상태 (키 불필요)"
	@echo "  make ssh        박스 접속 (ubuntu)"
	@echo "  make health     actuator health 조회"
	@echo "  make check      컨테이너/DB/볼륨 종합 점검"
	@echo "  make ps         컨테이너 상태"
	@echo "  make logs       실시간 로그"
	@echo ""
	@echo "운영 (Terraform):"
	@echo "  make bootstrap  state 버킷 생성(최초 1회)"
	@echo "  make up         apply + 앱 health 대기"
	@echo "  make down       destroy"
	@echo "  make redeploy   인스턴스 -replace + 재배포 (/data 보존)"
	@echo ""
	@echo "모든 타깃: export AWS_PROFILE=<프로파일> 필요"

# 운영 (Terraform)
bootstrap: _profile
	aws s3 mb s3://$(STATE_BUCKET) --region $(REGION) 2>/dev/null || echo "state 버킷 이미 존재"
	aws s3api put-bucket-versioning --bucket $(STATE_BUCKET) --versioning-configuration Status=Enabled
	@echo "✅ state 버킷 준비 완료 ($(STATE_BUCKET))"

up: _profile
	@echo "plan 확인 후 yes 입력. 'must be replaced' 있으면 중단."
	cd $(ENV_DIR) && \
	terraform init -input=false && \
	terraform apply && \
	ip=$$(terraform output -raw public_ip) && \
	iid=$(ssm_target) && \
	echo "공인 IP: $$ip — 앱 부팅(cloud-init→docker→기동) 대기 중..." && \
	{ for i in $$(seq 1 60); do \
	    if $(ssh_via_ssm) ubuntu@$$iid 'curl -fsS -m 5 http://localhost:$(MANAGEMENT_PORT)/actuator/health' >/dev/null 2>&1; then \
	      echo "✅ 앱 기동 완료 → http://$$ip"; exit 0; \
	    fi; \
	    echo "  health 대기... ($$i/60, 10s 간격)"; sleep 10; \
	  done; \
	  echo "⚠️ 10분 내 응답 없음. 로그: make logs"; \
	  exit 1; }

down: _profile
	@echo "destroy 대상 확인 후 yes 입력. /data(postgres, redis) 포함 전부 삭제됨."
	cd $(ENV_DIR) && terraform destroy

redeploy: _profile
	@echo "plan 확인 후 yes 입력. 인스턴스 교체, /data(postgres, redis)만 유지."
	cd $(ENV_DIR) && terraform apply -replace='module.app.aws_instance.this'
	@echo "인스턴스 재생성 완료. SSM 등록 대기 후 compose 재전송, latest 배포."
	@iid=$(ssm_target); \
	for i in $$(seq 1 30); do \
	  [ "$$(aws ssm describe-instance-information \
	      --filters "Key=InstanceIds,Values=$$iid" \
	      --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null)" = Online ] && break; \
	  echo "  SSM 등록 대기... ($$i/30, 10s 간격)"; sleep 10; \
	done; \
	INSTANCE_TAG=$(INSTANCE_TAG) AWS_REGION=$(REGION) bash .github/scripts/ssm-deploy.sh latest

# 공통
status: _profile
	@aws ec2 describe-instances \
	  --filters "Name=tag:Name,Values=$(INSTANCE_TAG)" "Name=instance-state-name,Values=running" \
	  --query "Reservations[0].Instances[0].{InstanceId:InstanceId,PublicIp:PublicIpAddress,State:State.Name}" \
	  --output table

health: _profile
	@echo "GET (SSM) http://localhost:$(MANAGEMENT_PORT)/actuator/health"; \
	$(ssh_to) 'curl -s http://localhost:$(MANAGEMENT_PORT)/actuator/health' | (jq . 2>/dev/null || cat); echo

check: _profile
	$(ssh_to) 'bash -s' < $(CHECK_SCRIPT)

ps: _profile
	$(ssh_to) 'sudo docker compose -f /opt/app/docker-compose.yml ps'

logs: _profile
	$(ssh_to) 'cd /opt/app && sudo docker compose logs -f --tail=50'

ssh: _profile
	$(ssh_to)
