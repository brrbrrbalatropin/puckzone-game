# Un solo Flexible Server (B1ms, lo mas barato) con una database por servicio:
# aislamiento logico suficiente para el proyecto sin pagar 3 servidores.
# La contrasena la genera Terraform y queda solo en el state (storage privado).

resource "random_password" "postgres" {
  length  = 32
  special = false # evita problemas de escape en JDBC URLs y env vars
}

resource "azurerm_postgresql_flexible_server" "main" {
  name                = "${var.project}-pg"
  location            = azurerm_resource_group.main.location
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
