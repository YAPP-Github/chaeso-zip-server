#!/usr/bin/env bash
# Deploy to the app instance via SSM: find it by tag, run /opt/app/deploy.sh <sha>
# on it, and fail if the command does not succeed.
# Usage: ssm-deploy.sh <git-sha>   (needs INSTANCE_TAG; AWS creds/region from the workflow)
set -euo pipefail

sha="${1:?usage: ssm-deploy.sh <git-sha>}"
: "${INSTANCE_TAG:?INSTANCE_TAG is required}"

instance_id=$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=${INSTANCE_TAG}" "Name=instance-state-name,Values=running" \
  --query "Reservations[].Instances[].InstanceId" --output text)
[ -n "$instance_id" ] || {
  echo "no running instance tagged ${INSTANCE_TAG}"
  exit 1
}
echo "target: $instance_id"

command_id=$(aws ssm send-command \
  --instance-ids "$instance_id" \
  --document-name AWS-RunShellScript \
  --comment "deploy ${sha:0:7}" \
  --parameters "$(jq -n --arg cmd "/opt/app/deploy.sh ${sha}" '{commands: [$cmd]}')" \
  --query "Command.CommandId" --output text)
echo "command: $command_id"

sleep 3
aws ssm wait command-executed --command-id "$command_id" --instance-id "$instance_id" || true

echo "----- stdout -----"
aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" \
  --query "StandardOutputContent" --output text
status=$(aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" \
  --query "Status" --output text)
echo "status: $status"
if [ "$status" != "Success" ]; then
  echo "----- stderr -----"
  aws ssm get-command-invocation --command-id "$command_id" --instance-id "$instance_id" \
    --query "StandardErrorContent" --output text
  exit 1
fi