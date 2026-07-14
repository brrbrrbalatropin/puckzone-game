# Un solo Flexible Server (B1ms, lo mas barato) con una database por servicio:
# aislamiento logico suficiente para el proyecto sin pagar 3 servidores.
# La contrasena la genera Terraform y queda solo en el state (storage privado).

resource "random_password" "postgres" {
  length  = 32
  special = false # evita problemas de escape en JDBC URLs y env vars
}

resource "azurerm_postgresql_flexible_server" "main" {
  # La region va en el nombre porque ARM tombstonea nombre+region en intentos
  # fallidos (paso con eastus y eastus2, restringidas para Azure for Students).
  name                = "${var.project}-postgres-${var.postgres_location}"
  location            = var.postgres_location
  resource_group_name = azurerm_resource_group.main.name

  version                      = "17"
  administrator_login          = var.postgres_admin_login
  administrator_password       = random_password.postgres.result
  sku_name                     = "B_Standard_B1ms"
  storage_mb                   = 32768
  backup_retention_days        = 7
  geo_redundant_backup_enabled = false

  # Sin VNet: acceso publico restringido por firewall (regla abajo).
  public_network_access_enabled = true

  # Azure asigno la zona 3 al crear el servidor; se fija aqui para que
  # terraform no intente moverlo (drift detectado 2026-07-07).
  zone = "3"
}

# 0.0.0.0-0.0.0.0 es la regla especial "permitir servicios de Azure":
# deja pasar a las Container Apps (que salen por IPs de Azure) sin abrir internet.
resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = "allow-azure-services"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

# Una database por servicio (game_db es para el historial del proximo sprint).
resource "azurerm_postgresql_flexible_server_database" "databases" {
  for_each  = toset(["auth_db", "ranking_db", "game_db"])
  name      = each.key
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# --- Endurecimiento de seguridad (hallazgos Trivy AZU-0019/0021/0024/0026) ---
# Parametros de servidor. Los servicios ya conectan con sslmode=require; esto lo
# fuerza tambien del lado del servidor (rechaza cualquier conexion sin TLS) y
# habilita el logging de auditoria. require_secure_transport y ssl_min_protocol_version
# son estaticos: aplicarlos reinicia el servidor una vez (segundos de corte).
locals {
  postgres_config = {
    "require_secure_transport" = "on"      # rechaza conexiones sin TLS (AZU-0026)
    "ssl_min_protocol_version" = "TLSv1.2" # prohibe TLS 1.0/1.1 (AZU-0026)
    "log_connections"          = "on"      # audita conexiones (AZU-0019)
    "log_disconnections"       = "on"      # audita cierres de conexion
    "log_checkpoints"          = "on"      # visibilidad de checkpoints (AZU-0024)
    "connection_throttle.enable" = "on"    # throttling anti abuso (AZU-0021)
  }
}

resource "azurerm_postgresql_flexible_server_configuration" "hardening" {
  for_each  = local.postgres_config
  name      = each.key
  server_id = azurerm_postgresql_flexible_server.main.id
  value     = each.value
}
