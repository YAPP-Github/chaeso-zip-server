#!/usr/bin/env bash
# Deploy to the app instance via SSM: find it by tag, ship repo's deploy artifacts
# (docker-compose.yml, Caddyfile, deploy.sh), run deploy.sh <sha> on it,
# and fail if the command does not succeed. TLS certs come from Doppler, not the repo.
# Usage: ssm-deploy.sh <git-sha|latest>
set -euo pipefail

sha="${1:?usage: ssm-deploy.sh <git-sha|latest>}"

[[ "$sha" =~ ^([0-9a-f]{7,40}|latest)$ ]] || { echo "invalid ref: $sha"; exit 1; }
: "${INSTANCE_TAG:?INSTANCE_TAG is required}"

deploy_dir="$(dirname "$0")/../../deploy"
compose_b64=$(base64 < "$deploy_dir/docker-compose.yml" | tr -d '\n')
caddyfile_b64=$(base64 < "$deploy_dir/Caddyfile" | tr -d '\n')
deploysh_b64=$(base64 < "$(dirname "$0")/../../infra/modules/aws/app/remote-deploy.sh" | tr -d '\n')

instance_id=$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=${INSTANCE_TAG}" "Name=instance-state-name,Values=running" \
  --query "Reservations[].Instances[].InstanceId" --output text)
[ -n "$instance_id" ] || {
  echo "no running instance tagged ${INSTANCE_TAG}"
  exit 1
}
echo "target: $instance_id"

params=$(jq -n --arg compose "$compose_b64" --arg caddy "$caddyfile_b64" --arg deploysh "$deploysh_b64" --arg sha "$sha" '{commands: [
  "echo \($compose) | base64 -d > /opt/app/docker-compose.yml",
  "echo \($caddy) | base64 -d > /opt/app/Caddyfile",
  "echo \($deploysh) | base64 -d > /opt/app/deploy.sh",
  "chmod +x /opt/app/deploy.sh",
  "/opt/app/deploy.sh \($sha)"
]}')
command_id=$(aws ssm send-command \
  --instance-ids "$instance_id" \
  --document-name AWS-RunShellScript \
  --comment "deploy ${sha:0:7}" \
  --parameters "$params" \
  --query "Command.CommandId" --output text)
echo "command: $command_id"

deadline=$(( SECONDS + 600 ))
while :; do
  status=$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" \
    --query "Status" --output text 2>/dev/null || echo Pending)
  case "$status" in Success|Failed|Cancelled|TimedOut) break ;; esac
  [ "$SECONDS" -lt "$deadline" ] || { status=TimedOut; break; }
  sleep 5
done

echo "----- stdout -----"
aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" \
  --query "StandardOutputContent" --output text
echo "status: $status"
if [ "$status" != "Success" ]; then
  echo "----- stderr -----"
  aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" \
    --query "StandardErrorContent" --output text
  exit 1
fi