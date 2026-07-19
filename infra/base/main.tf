# Recursos compartidos de la plataforma PuckZone.
# Los servicios individuales (sus Container Apps) viven en el infra/app de cada repo.

resource "azurerm_resource_group" "main" {
  name     = "${var.project}-rg"
  location = var.location
}

# Observabilidad: Log Analytics recibe los logs de consola de todas las
# Container Apps del environment; App Insights es el APM (metricas, trazas).
resource "azurerm_log_analytics_workspace" "main" {
  name                = "${var.project}-logs"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
}

resource "azurerm_application_insights" "main" {
  name                = "${var.project}-appinsights"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "java"
}

# El environment es la "red" logica donde corren las Container Apps de los
# 6 servicios: les da DNS interno entre ellas y el plan de consumo (serverless).
resource "azurerm_container_app_environment" "main" {
  name                       = "${var.project}-env"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id

  # Cifra el trafico entre apps del environment (peer authentication mTLS de
  # ACA): el HTTP plano interno (http://puckzone-<svc>) viaja cifrado por
  # debajo sin que los servicios cambien nada.
  mutual_tls_enabled = true
}
