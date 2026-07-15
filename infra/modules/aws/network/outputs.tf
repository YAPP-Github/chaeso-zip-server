output "subnet_id" {
  value = aws_subnet.this.id
}

output "firewall_ref" {
  value = aws_security_group.this.id
}
