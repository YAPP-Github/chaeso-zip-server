terraform {
  required_version = ">= 1.10"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region
}

resource "aws_key_pair" "this" {
  key_name   = "chaeso-zip-key"
  public_key = var.ssh_pubkey
}

module "network" {
  source            = "../../../modules/aws/network"
  availability_zone = var.availability_zone
  allowed_ports     = ["80", "443"]
}

module "app" {
  source                          = "../../../modules/aws/app"
  subnet_id                       = module.network.subnet_id
  firewall_ref                    = module.network.firewall_ref
  key_name                        = aws_key_pair.this.key_name
  app_image_repo                  = aws_ecr_repository.app.repository_url
  embedded_db                     = true
  db_host                         = "db"
  redis_host                      = "redis"
  instance_type                   = "t3.small"
  doppler_token                   = var.doppler_token
  grafana_prom_url                = var.grafana_prom_url
  grafana_prom_user               = var.grafana_prom_user
  grafana_token                   = var.grafana_token
  ad_history_cors_allowed_origins = var.ad_history_cors_allowed_origins
}

output "public_ip" {
  value = module.app.public_ip
}

output "ad_history_bucket_name" {
  value = module.app.ad_history_bucket_name
}
