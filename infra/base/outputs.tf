# Contrato para los infra/app de cada repo de servicio: los leen con
# data.terraform_remote_state apuntando a base.tfstate.

output "resource_group_name" {
  value = azurerm_resource_group.main.name
}

output "location" {
  value = azurerm_resource_group.main.location
}

output "container_app_environment_id" {
  value = azurerm_container_app_environment.main.id
}

output "container_app_environment_default_domain" {
  description = "Dominio del environment; las apps internas resuelven como <app>.internal.<dominio>"
  value       = azurerm_container_app_environment.main.default_domain
}

output "redis_host" {
  description = "Host interno de Redis para las apps del environment"
  value       = azurerm_container_app.redis.name
}

output "postgres_fqdn" {
  value = azurerm_postgresql_flexible_server.main.fqdn
}

output "postgres_admin_login" {
  value = var.postgres_admin_login
}

output "postgres_admin_password" {
  value     = random_password.postgres.result
  sensitive = true
}

output "jwt_secret" {
  description = "PUCKZONE_JWT_SECRET de produccion; auth firma y el resto valida con el"
  value       = random_password.jwt_secret.result
  sensitive   = true
}

output "application_insights_connection_string" {
  value     = azurerm_application_insights.main.connection_string
  sensitive = true
}

output "metrics_token" {
  description = "Token Bearer que exige /actuator/prometheus del gateway (PUCKZONE_METRICS_TOKEN); Prometheus lo manda en el scrape"
  value       = random_password.metrics_token.result
  sensitive   = true
}

output "grafana_url" {
  value = "https://${azurerm_container_app.grafana.ingress[0].fqdn}"
}

output "grafana_admin_password" {
  description = "Password del usuario admin de Grafana"
  value       = random_password.grafana_admin.result
  sensitive   = true
}
