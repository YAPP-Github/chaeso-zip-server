#!/usr/bin/env bash
# Deploy to the app instance via SSM: find it by tag, ship repo's docker-compose.yml,
# run /opt/app/deploy.sh <sha> on it, and fail if the command does not succeed.
# Usage: ssm-deploy.sh <git-sha>   (needs INSTANCE_TAG; AWS creds/region from the workflow)
set -euo pipefail

sha="${1:?usage: ssm-deploy.sh <git-sha>}"

[[ "$sha" =~ ^[0-9a-f]{7,40}$ ]] || { echo "invalid sha: $sha"; exit 1; }
: "${INSTANCE_TAG:?INSTANCE_TAG is required}"

compose_path="$(dirname "$0")/../../deploy/docker-compose.yml"
compose_b64=$(base64 < "$compose_path" | tr -d '\n')

instance_id=$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=${INSTANCE_TAG}" "Name=instance-state-name,Values=running" \
  --query "Reservations[].Instances[].InstanceId" --output text)
[ -n "$instance_id" ] || {
  echo "no running instance tagged ${INSTANCE_TAG}"
  exit 1
}
echo "target: $instance_id"

params=$(jq -n --arg b64 "$compose_b64" --arg sha "$sha" '{commands: [
  "mkdir -p /opt/app",
  "echo \($b64) | base64 -d > /opt/app/docker-compose.yml",
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