resource "aws_iam_policy" "ssm_shell" {
  name = "chaeso-zip-ssm-shell"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Action    = "ssm:StartSession"
        Resource  = "arn:aws:ec2:${var.region}:${data.aws_caller_identity.current.account_id}:instance/*"
        Condition = { StringEquals = { "ssm:resourceTag/Name" = "chaeso-zip-vm" } }
      },
      {
        Effect = "Allow"
        Action = "ssm:StartSession"
        Resource = [
          "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:document/SSM-SessionManagerRunShell",
          "arn:aws:ssm:${var.region}::document/AWS-StartPortForwardingSession",
          "arn:aws:ssm:${var.region}::document/AWS-StartSSHSession",
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:TerminateSession", "ssm:ResumeSession"]
        Resource = "arn:aws:ssm:*:*:session/$${aws:username}-*"
      },
      {
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_user" "teammate" {
  count = var.teammate_username != "" ? 1 : 0
  name  = var.teammate_username
}

resource "aws_iam_user_policy_attachment" "teammate_ssm" {
  count      = var.teammate_username != "" ? 1 : 0
  user       = aws_iam_user.teammate[0].name
  policy_arn = aws_iam_policy.ssm_shell.arn
}

resource "aws_iam_access_key" "teammate" {
  count = var.teammate_username != "" ? 1 : 0
  user  = aws_iam_user.teammate[0].name
}

output "teammate_access_key_id" {
  value = try(aws_iam_access_key.teammate[0].id, null)
}

output "teammate_secret_access_key" {
  value     = try(aws_iam_access_key.teammate[0].secret, null)
  sensitive = true
}
