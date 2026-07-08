# Redis corre como una Container App mas (decision 2026-07-07): game solo guarda
# snapshots que expiran a 1h, no necesita durabilidad ni el costo de Azure Cache.
# Ingress interno TCP: solo alcanzable desde apps del mismo environment, por eso
# va sin contrasena.

resource "azurerm_container_app" "redis" {
  name                         = "${var.project}-redis"
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  revision_mode                = "Single"

  template {
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "redis"
      image  = "redis:7-alpine"
      cpu    = 0.25
      memory = "0.5Gi"
    }
  }

  ingress {
    external_enabled = false
    transport        = "tcp"
    target_port      = 6379
    exposed_port     = 6379

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }
}
