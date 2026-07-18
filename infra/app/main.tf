# Sharding por partida: cada Container App (shard) es duena absoluta de sus
# salas — matchmaking asigna cada partida a un shard y el cliente conecta su
# WS al shard dueno via gateway (/ws-{shard}). Escalar game = agregar shards
# aqui (y en GAME_SHARD_URLS de matchmaking + rutas del gateway), NO subir
# replicas. El shard 0 conserva el nombre historico puckzone-game para no
# recrear la app ni tocar las URLs internas que ya la referencian.
locals {
  game_shards = {
    "0" = "puckzone-game"
    "1" = "puckzone-game-1"
  }
}

# El puckzone-game original ahora es el shard 0 del for_each.
moved {
  from = azurerm_container_app.game
  to   = azurerm_container_app.game["0"]
}

resource "azurerm_container_app" "game" {
  for_each                     = local.game_shards
  name                         = each.value
  resource_group_name          = data.terraform_remote_state.base.outputs.resource_group_name
  container_app_environment_id = data.terraform_remote_state.base.outputs.container_app_environment_id
  revision_mode                = "Single"

  template {
    # Las salas y las sesiones STOMP viven en la memoria DE ESTE shard: la
    # replica unica por shard es de diseno (escalar = mas shards, arriba).
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "game"
      image  = var.image
      # 1 vCPU tras la prueba de carga del 2026-07-14: con 0.5 vCPU el loop
      # de fisica (un hilo a 60Hz para todas las salas) saturaba con 10
      # partidas simultaneas (salas a 32Hz, p95 84ms); con 1 vCPU las 20
      # partidas del KPI corren a 60Hz con p99 21ms y ~70% de CPU.
      cpu    = 1.0
      memory = "2Gi"

      # game_db (amigos y mensajes directos). La URL completa via env var
      # porque Azure Flexible Server exige TLS: sslmode=require.
      env {
        name  = "SPRING_DATASOURCE_URL"
        value = "jdbc:postgresql://${data.terraform_remote_state.base.outputs.postgres_fqdn}:5432/game_db?sslmode=require"
      }
      env {
        name  = "SPRING_DATASOURCE_USERNAME"
        value = data.terraform_remote_state.base.outputs.postgres_admin_login
      }
      env {
        name        = "SPRING_DATASOURCE_PASSWORD"
        secret_name = "db-password"
      }
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
      # Reporte de resultados a ranking por el HTTP plano interno del environment.
      env {
        name  = "RANKING_BASE_URL"
        value = "http://puckzone-ranking"
      }
      # Secreto JWT de produccion (HS512, generado en infra/base): game valida
      # el ?token= del handshake WS localmente, igual que gateway/matchmaking.
      env {
        name        = "PUCKZONE_JWT_SECRET"
        secret_name = "jwt-secret"
      }
      # Origenes permitidos en el handshake SockJS (el Origin del navegador
      # llega intacto a traves del proxy del gateway). Sin esto, game solo
      # acepta los localhost del application.yaml y rechaza al frontend
      # desplegado. Nombre sin guiones: relaxed binding de Spring para
      # puckzone.websocket.allowed-origins.
      env {
        name  = "PUCKZONE_WEBSOCKET_ALLOWEDORIGINS"
        value = "https://puckzone-frontend.calmgrass-8fe4a577.eastus.azurecontainerapps.io,http://localhost:5173,http://localhost:8080"
      }
      # Identidad del shard (0 = puckzone-game, 1 = puckzone-game-1): viaja
      # en el indice active-game:{userId} de Redis para la reconexion.
      # Al final de la lista: el provider compara las env por posicion y
      # insertarla en otro lado ensucia el diff de todas las demas.
      env {
        name  = "PUCKZONE_SHARD_ID"
        value = each.key
      }

      # Con JPA el arranque pasó de ~25s a ~40s: sin initial_delay la
      # liveness (default ACA ~1s + 3 fallos x10s) mataba el contenedor a
      # los ~30s en bucle infinito, siempre justo antes de que Tomcat
      # abriera el puerto (visto 2026-07-12 en las revisiones 24-27).
      liveness_probe {
        transport     = "HTTP"
        port          = 8083
        path          = "/actuator/health/liveness"
        initial_delay = 60
      }
      readiness_probe {
        transport               = "HTTP"
        port                    = 8083
        path                    = "/actuator/health/readiness"
        initial_delay           = 20
        failure_count_threshold = 10
      }
    }
  }

  secret {
    name  = "jwt-secret"
    value = data.terraform_remote_state.base.outputs.jwt_secret
  }
  secret {
    name  = "db-password"
    value = data.terraform_remote_state.base.outputs.postgres_admin_password
  }

  # Interno: solo el gateway (en el mismo environment) le habla; nada de internet.
  # allow_insecure permite trafico HTTP plano entre apps del environment (el
  # redirect a HTTPS rompe las llamadas: cert de .internal. no confiable en Java).
  # transport "http" (HTTP/1.1 explicito) y NO "auto": con auto, el upgrade de
  # WebSocket del gateway moria en el ingress interno ("Connection prematurely
  # closed BEFORE opening handshake is complete"); el upgrade WS es de HTTP/1.1.
  ingress {
    external_enabled           = false
    target_port                = 8083
    transport                  = "http"
    allow_insecure_connections = true

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
