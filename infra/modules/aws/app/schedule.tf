resource "aws_iam_role" "scheduler" {
  name = "${var.name_prefix}-scheduler-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "scheduler" {
  role = aws_iam_role.scheduler.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ec2:StartInstances", "ec2:StopInstances"]
      Resource = aws_instance.this.arn
    }]
  })
}

resource "aws_scheduler_schedule" "stop" {
  name = "${var.name_prefix}-stop-night"
  flexible_time_window {
    mode = "OFF"
  }
  schedule_expression          = "cron(0 0 * * ? *)"
  schedule_expression_timezone = "Asia/Seoul"
  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ InstanceIds = [aws_instance.this.id], Force = false })

    retry_policy {
      maximum_retry_attempts       = 3
      maximum_event_age_in_seconds = 3600
    }
  }
}

resource "aws_scheduler_schedule" "start" {
  name = "${var.name_prefix}-start-morning"
  flexible_time_window {
    mode = "OFF"
  }
  schedule_expression          = "cron(0 9 * * ? *)"
  schedule_expression_timezone = "Asia/Seoul"
  target {
    arn      = "arn:aws:scheduler:::aws-sdk:ec2:startInstances"
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ InstanceIds = [aws_instance.this.id] })

    retry_policy {
      maximum_retry_attempts       = 3
      maximum_event_age_in_seconds = 3600
    }
  }
}
