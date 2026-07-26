resource "grafana_dashboard" "app" {
  config_json = file("${path.module}/dashboards/chaeso-zip-app.json")
}

resource "grafana_dashboard" "infra" {
  config_json = file("${path.module}/dashboards/chaeso-zip-infra.json")
}
