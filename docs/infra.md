# 인프라 구조 / 접속 가이드

## 1. 코드 구조

```
Makefile                         # 인프라 명령 (up/down/health/logs/ssh …)
infra/
├── check.sh                     # 인프라 상태 check
├── environments/aws/prod/
│   ├── backend.tf               # S3 state + native lock
│   ├── main.tf
│   ├── cicd.tf                  # ECR, GitHub OIDC, 배포 role
│   ├── team-access.tf           # SSM 접속용 IAM 사용자/정책
│   └── variables.tf
└── modules/aws/
    ├── network/                 # VPC/subnet/IGW/SG
    └── app/                     # EC2/EIP/EBS/instance profile + startup-script
```

---

## 2. 접속 방법

로컬에 AWS CLI + [session-manager-plugin]이 필요하고, IAM에 세션 권한이 있어야 합니다.

[session-manager-plugin]: https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html

**1) 사전 설치 (최초 1회)** — AWS CLI + SSM 플러그인

```bash
# macOS
brew install awscli
brew install --cask session-manager-plugin
```
**2) 프로파일 등록 (최초 1회)**

```bash
aws configure --profile [프로파일 이름]
# AWS Access Key ID
# AWS Secret Access Key
# Default region name
# Default output format
```

**3) 박스 접속 (SSH-over-SSM)**

`ubuntu` 계정으로 접속합니다.`~/.ssh/chaeso-zip` 개인키가 있어야 하고,

`[프로파일이름]`은 2)에서 등록한 이름과 동일하게 넣어야 합니다.

인스턴스 ID는 태그로 자동 조회하므로, 박스가 교체돼도 이 명령을 고칠 필요가 없습니다.

```bash
ssh -i ~/.ssh/chaeso-zip -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ProxyCommand="aws ssm start-session --profile [프로파일이름] --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p" ubuntu@$(aws ec2 describe-instances --profile [프로파일이름] --filters "Name=tag:Name,Values=chaeso-zip-vm" "Name=instance-state-name,Values=running" --query "Reservations[0].Instances[0].InstanceId" --output text)
```

### 2-1. Makefile

```bash
export AWS_PROFILE=[프로파일 이름]   # 모든 make 타깃은 프로파일 필요
make ssh       # 박스 진입(ubuntu)
make health    # actuator health (SSM 터널로 조회)
make check     # 컨테이너·health·db/redis·볼륨 종합 점검
make logs      # 실시간 로그
make ps        # 컨테이너 상태
```

---

## 3. Makefile 명령

모든 타깃은 `export AWS_PROFILE=<프로파일>`이 필요합니다. 전체 목록은 `make help`.

### 공통

| 명령 | 하는 일 |
|---|---|
| `make status` | 인스턴스 ID, 공인 IP, 상태 (키 불필요, EC2 조회) |
| `make ssh` | 박스 접속 (ubuntu) |
| `make health` | actuator health 조회 |
| `make check` | 컨테이너/DB/볼륨 종합 점검 |
| `make ps` | 컨테이너 상태 |
| `make logs` | 실시간 로그 |