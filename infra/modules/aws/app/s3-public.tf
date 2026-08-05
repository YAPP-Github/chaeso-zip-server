resource "aws_s3_bucket" "public" {
  bucket = "${var.name_prefix}-public-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_logging" "public" {
  bucket        = aws_s3_bucket.public.id
  target_bucket = aws_s3_bucket.s3_access_logs.id
  target_prefix = "public/"
}

resource "aws_s3_bucket_public_access_block" "public" { # NOSONAR
  bucket = aws_s3_bucket.public.id

  # public read-only bucket for static assets (logos, profile images); write access is IAM-gated
  block_public_acls       = true
  block_public_policy     = false
  ignore_public_acls      = true
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "public" {
  bucket = aws_s3_bucket.public.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "PublicReadGetObject"
        Effect    = "Allow"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.public.arn}/*"
      },
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.public.arn,
          "${aws_s3_bucket.public.arn}/*",
        ]
        Condition = {
          Bool = { "aws:SecureTransport" = "false" }
        }
      },
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.public]
}
