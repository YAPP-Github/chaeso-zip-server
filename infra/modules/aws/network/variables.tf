variable "name_prefix" {
  type    = string
  default = "chaeso-zip"
}

variable "vpc_cidr" {
  type    = string
  default = "10.10.0.0/16"
}

variable "subnet_cidr" {
  type    = string
  default = "10.10.0.0/24"
}

variable "availability_zone" {
  type = string
}

variable "allowed_ports" {
  type    = list(string)
  default = ["80", "443"]
}
