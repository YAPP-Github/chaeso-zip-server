variable "name_prefix" {
  type    = string
  default = "chaeso-zip"
}

variable "subnet_id" {
  type = string
}

variable "firewall_ref" {
  type = string
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "embedded_db" {
  type    = bool
  default = false
}

variable "key_name" {
  type = string
}

variable "app_image_repo" {
  type = string
}

variable "image_tag" {
  type    = string
  default = "latest"
}

variable "app_port" {
  type    = number
  default = 8080
}

variable "management_port" {
  type    = number
  default = 8081
}

variable "data_volume_size_gb" {
  type    = number
  default = 10
}

variable "db_host" {
  type = string
}

variable "redis_host" {
  type = string
}

variable "redis_port" {
  type    = number
  default = 6379
}

variable "db_name" {
  type    = string
  default = "chaeso"
}

variable "db_user" {
  type    = string
  default = "chaeso"
}

variable "doppler_token" {
  type      = string
  default   = ""
  sensitive = true
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

variable "ad_history_cors_allowed_origins" {
  type    = list(string)
  default = ["http://localhost:3000"]
}