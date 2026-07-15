data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
}


resource "aws_iam_role" "this" {
  name = "${var.name_prefix}-app-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# ECR 이미지 pull용 읽기 권한
resource "aws_iam_role_policy_attachment" "ecr_read" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_instance_profile" "this" {
  name = "${var.name_prefix}-app-profile"
  role = aws_iam_role.this.name
}

resource "aws_instance" "this" {
  #checkov:skip=CKV_AWS_88:single-instance public API must be internet-reachable
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.firewall_ref]
  key_name               = var.key_name
  iam_instance_profile   = aws_iam_instance_profile.this.name

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  user_data = templatefile("${path.module}/startup-script.sh.tftpl", {
    app_image_repo  = var.app_image_repo
    image_tag       = var.image_tag
    ecr_registry    = split("/", var.app_image_repo)[0]
    deploy_script   = file("${path.module}/remote-deploy.sh")
    doppler_token   = var.doppler_token
    app_port        = var.app_port
    management_port = var.management_port
    db_host         = var.db_host
    db_name         = var.db_name
    db_user         = var.db_user
    redis_host      = var.redis_host
    redis_port      = var.redis_port
    embedded_db     = var.embedded_db

    compose_profiles = join(",", compact([
      var.embedded_db ? "embedded" : "",
      var.grafana_prom_url != "" ? "monitoring" : "",
    ]))

    grafana_prom_url  = var.grafana_prom_url
    grafana_prom_user = var.grafana_prom_user
    grafana_token     = var.grafana_token
  })

  tags = { Name = "${var.name_prefix}-vm" }

  root_block_device {
    encrypted = true
  }

  lifecycle {
    ignore_changes = [ami]
  }
}

resource "aws_eip" "this" {
  instance = aws_instance.this.id
  domain   = "vpc"
  tags     = { Name = "${var.name_prefix}-ip" }
}

resource "aws_ebs_volume" "data" {
  count             = var.embedded_db ? 1 : 0
  availability_zone = aws_instance.this.availability_zone
  size              = var.data_volume_size_gb
  type              = "gp3"
  encrypted         = true
  tags              = { Name = "${var.name_prefix}-data" }
}

resource "aws_volume_attachment" "data" {
  count                          = var.embedded_db ? 1 : 0
  device_name                    = "/dev/xvdf"
  volume_id                      = aws_ebs_volume.data[0].id
  instance_id                    = aws_instance.this.id
  stop_instance_before_detaching = true
}
