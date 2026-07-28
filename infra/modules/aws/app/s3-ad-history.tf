data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "ad_history" {
  bucket = "${var.name_prefix}-ad-history-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_logging" "ad_history" {
  bucket        = aws_s3_bucket.ad_history.id
  target_bucket = aws_s3_bucket.s3_access_logs.id
  target_prefix = "ad-history/"
}

resource "aws_s3_bucket" "s3_access_logs" {
  bucket = "${var.name_prefix}-s3-access-logs-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "s3_access_logs" {
  bucket = aws_s3_bucket.s3_access_logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "s3_access_logs" {
  bucket = aws_s3_bucket.s3_access_logs.id

  rule {
    id     = "expire-old-logs"
    status = "Enabled"

    filter {}

    expiration {
      days = 90
    }
  }
}

resource "aws_s3_bucket_policy" "s3_access_logs" {
  bucket = aws_s3_bucket.s3_access_logs.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "S3ServerAccessLogsPolicy"
        Effect    = "Allow"
        Principal = { Service = "logging.s3.amazonaws.com" }
        Action    = "s3:PutObject"
        Resource  = "${aws_s3_bucket.s3_access_logs.arn}/*"
        Condition = {
          ArnLike      = { "aws:SourceArn" = aws_s3_bucket.ad_history.arn }
          StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
        }
      },
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.s3_access_logs.arn,
          "${aws_s3_bucket.s3_access_logs.arn}/*",
        ]
        Condition = {
          Bool = { "aws:SecureTransport" = "false" }
        }
      },
    ]
  })
}

resource "aws_s3_bucket_public_access_block" "ad_history" {
  bucket = aws_s3_bucket.ad_history.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "ad_history" {
  bucket = aws_s3_bucket.ad_history.id

  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = var.ad_history_cors_allowed_origins
    allowed_headers = ["*"]
    max_age_seconds = 3000
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "ad_history" {
  bucket = aws_s3_bucket.ad_history.id

  rule {
    id     = "expire-pending-uploads"
    status = "Enabled"

    filter {
      tag {
        key   = "retain"
        value = "pending"
      }
    }

    expiration {
      days = 1
    }
  }
}

resource "aws_s3_bucket_policy" "ad_history_https_only" {
  bucket = aws_s3_bucket.ad_history.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource = [
        aws_s3_bucket.ad_history.arn,
        "${aws_s3_bucket.ad_history.arn}/*",
      ]
      Condition = {
        Bool = { "aws:SecureTransport" = "false" }
      }
    }]
  })
}

resource "aws_iam_role_policy" "ad_history_s3" {
  name = "${var.name_prefix}-ad-history-s3"
  role = aws_iam_role.this.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:PutObjectTagging", "s3:GetObject"]
      Resource = "${aws_s3_bucket.ad_history.arn}/*"
    }]
  })
}
