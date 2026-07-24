output "public_ip" {
  value = aws_eip.this.public_ip
}

output "ad_history_bucket_name" {
  value = aws_s3_bucket.ad_history.bucket
}
