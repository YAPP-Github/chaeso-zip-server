variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "availability_zone" {
  type    = string
  default = "ap-northeast-2a"
}

variable "github_repo" {
  type    = string
  default = "YAPP-Github/chaeso-zip-server"
}

variable "teammate_username" {
  type    = string
  default = "chaeso-zip-backend"
}

variable "ssh_pubkey" {
  type = string
}

variable "grafana_prom_url" {
  type    = string
  default = ""
}

variable "grafana_prom_user" {
  type    = string
  default = ""
}

variable "grafana_token" {
  type      = string
  default   = ""
  sensitive = true
}

variable "doppler_token" {
  type      = string
  default   = ""
  sensitive = true
}

variable "ad_history_cors_allowed_origins" {
  type    = list(string)
  default = ["http://localhost:3000"]
}
