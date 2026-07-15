terraform {
  backend "s3" {
    bucket = "chaeso-zip-tfstate"
    key    = "aws/prod/terraform.tfstate"
    region = "ap-northeast-2"

    use_lockfile = true
  }
}
