resource "aws_s3_bucket" "ad_history_dev" {
  bucket = "chaeso-zip-ad-history-dev-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "ad_history_dev" {
  bucket = aws_s3_bucket.ad_history_dev.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "ad_history_dev" {
  bucket = aws_s3_bucket.ad_history_dev.id

  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = ["http://localhost:3000"]
    allowed_headers = ["*"]
    max_age_seconds = 3000
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "ad_history_dev" {
  bucket = aws_s3_bucket.ad_history_dev.id

  rule {
    id     = "expire-all"
    status = "Enabled"

    filter {}

    expiration {
      days = 7
    }
  }
}

resource "aws_s3_bucket_policy" "ad_history_dev_https_only" {
  bucket = aws_s3_bucket.ad_history_dev.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource = [
        aws_s3_bucket.ad_history_dev.arn,
        "${aws_s3_bucket.ad_history_dev.arn}/*",
      ]
      Condition = {
        Bool = { "aws:SecureTransport" = "false" }
      }
    }]
  })
}

resource "aws_iam_user" "ad_history_dev" {
  name = "chaeso-zip-ad-history-dev"
}

resource "aws_iam_user_policy" "ad_history_dev" {
  name = "chaeso-zip-ad-history-dev-s3"
  user = aws_iam_user.ad_history_dev.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:PutObjectTagging", "s3:GetObject"]
      Resource = "${aws_s3_bucket.ad_history_dev.arn}/*"
    }]
  })
}

output "ad_history_dev_bucket_name" {
  value = aws_s3_bucket.ad_history_dev.bucket
}
