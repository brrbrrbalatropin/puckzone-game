resource "azurerm_container_app" "game" {
  name                         = "puckzone-game"
  resource_group_name          = data.terraform_remote_state.base.outputs.resource_group_name
  container_app_environment_id = data.terraform_remote_state.base.outputs.container_app_environment_id
  revision_mode                = "Single"

  template {
    # Game guarda las salas en memoria y las sesiones STOMP viven en la
    # instancia: con mas de 1 replica los jugadores de una misma partida
    # podrian caer en instancias distintas. NO subir hasta resolver eso.
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "game"
      image  = var.image
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_DATA_REDIS_HOST"
        value = data.terraform_remote_state.base.outputs.redis_host
      }
      env {
        name  = "SPRING_DATA_REDIS_PORT"
        value = "6379"
      }
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = data.terraform_remote_state.base.outputs.application_insights_connection_string
      }

      liveness_probe {
        transport = "HTTP"
        port      = 8083
        path      = "/actuator/health/liveness"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8083
        path      = "/actuator/health/readiness"
      }
    }
  }

  # Interno: solo el gateway (en el mismo environment) le habla; nada de internet.
  ingress {
    external_enabled = false
    target_port      = 8083
    transport        = "auto"

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  lifecycle {
    # El pipeline actualiza la imagen con az containerapp update; sin esto,
    # cada terraform apply intentaria devolver la app a la imagen inicial.
    ignore_changes = [template[0].container[0].image]
  }
}
