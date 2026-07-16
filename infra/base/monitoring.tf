# Observabilidad "de verdad" (2026-07-16): Prometheus + Grafana como Container
# Apps del environment, pedidas por el usuario como sprint del foco de
# escalabilidad/disponibilidad. Los 5 servicios Java exponen
# /actuator/prometheus (micrometer-registry-prometheus); Prometheus los raspa
# cada 15s y Grafana (publica, con password) los grafica.
#
# La configuracion (prometheus.yml, provisioning y dashboards de Grafana) vive
# en el share de Azure Files "monitoring" y la sube terraform desde
# infra/base/monitoring/ — editar el archivo local + apply + revision restart.
# El TSDB de Prometheus y el sqlite de Grafana son EFIMEROS (se pierden al
# reiniciar la revision): las graficas historicas duran lo que dure la
# revision, y todo dashboard permanente se agrega como JSON provisionado.

# El nombre del storage account es global en Azure: sufijo aleatorio.
resource "random_string" "monitoring_suffix" {
  length  = 6
  lower   = true
  upper   = false
  numeric = true
  special = false
}

resource "azurerm_storage_account" "monitoring" {
  name                     = "${var.project}mon${random_string.monitoring_suffix.result}"
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"
}

resource "azurerm_storage_share" "monitoring" {
  name                 = "monitoring"
  storage_account_name = azurerm_storage_account.monitoring.name
  quota                = 1
}

# La API de Files crea un nivel de directorio a la vez: cadena de depends_on.
resource "azurerm_storage_share_directory" "grafana" {
  name             = "grafana"
  storage_share_id = azurerm_storage_share.monitoring.id
}

resource "azurerm_storage_share_directory" "grafana_provisioning" {
  name             = "grafana/provisioning"
  storage_share_id = azurerm_storage_share.monitoring.id
  depends_on       = [azurerm_storage_share_directory.grafana]
}

# datasources y dashboards llevan archivos; el resto son subcarpetas que el
# provisioning de Grafana espera encontrar (sin ellas solo loguea warnings,
# pero mejor no darle motivos).
resource "azurerm_storage_share_directory" "grafana_provisioning_subdirs" {
  for_each         = toset(["datasources", "dashboards", "plugins", "alerting", "notifiers"])
  name             = "grafana/provisioning/${each.value}"
  storage_share_id = azurerm_storage_share.monitoring.id
  depends_on       = [azurerm_storage_share_directory.grafana_provisioning]
}

resource "azurerm_storage_share_directory" "grafana_dashboards" {
  name             = "grafana/dashboards"
  storage_share_id = azurerm_storage_share.monitoring.id
  depends_on       = [azurerm_storage_share_directory.grafana]
}

# content_md5 fuerza el re-upload cuando el archivo local cambia.
resource "azurerm_storage_share_file" "prometheus_config" {
  name             = "prometheus.yml"
  storage_share_id = azurerm_storage_share.monitoring.id
  source           = "${path.module}/monitoring/prometheus.yml"
  content_md5      = filemd5("${path.module}/monitoring/prometheus.yml")
}

resource "azurerm_storage_share_file" "grafana_datasource" {
  name             = "datasource.yaml"
  path             = "grafana/provisioning/datasources"
  storage_share_id = azurerm_storage_share.monitoring.id
  source           = "${path.module}/monitoring/grafana/provisioning/datasources/datasource.yaml"
  content_md5      = filemd5("${path.module}/monitoring/grafana/provisioning/datasources/datasource.yaml")
  depends_on       = [azurerm_storage_share_directory.grafana_provisioning_subdirs]
}

resource "azurerm_storage_share_file" "grafana_dashboard_provider" {
  name             = "provider.yaml"
  path             = "grafana/provisioning/dashboards"
  storage_share_id = azurerm_storage_share.monitoring.id
  source           = "${path.module}/monitoring/grafana/provisioning/dashboards/provider.yaml"
  content_md5      = filemd5("${path.module}/monitoring/grafana/provisioning/dashboards/provider.yaml")
  depends_on       = [azurerm_storage_share_directory.grafana_provisioning_subdirs]
}

resource "azurerm_storage_share_file" "grafana_dashboard_puckzone" {
  name             = "puckzone.json"
  path             = "grafana/dashboards"
  storage_share_id = azurerm_storage_share.monitoring.id
  source           = "${path.module}/monitoring/grafana/dashboards/puckzone.json"
  content_md5      = filemd5("${path.module}/monitoring/grafana/dashboards/puckzone.json")
  depends_on       = [azurerm_storage_share_directory.grafana_dashboards]
}

# El share montado (solo lectura: es pura configuracion; los datos de
# Prometheus/Grafana quedan en el filesystem efimero del contenedor, lejos
# de los problemas de locking de sqlite sobre SMB).
resource "azurerm_container_app_environment_storage" "monitoring" {
  name                         = "monitoring"
  container_app_environment_id = azurerm_container_app_environment.main.id
  account_name                 = azurerm_storage_account.monitoring.name
  share_name                   = azurerm_storage_share.monitoring.name
  access_key                   = azurerm_storage_account.monitoring.primary_access_key
  access_mode                  = "ReadOnly"
}

# Token estatico que el MetricsTokenFilter del gateway exige en su
# /actuator/prometheus (la unica app publica; sin esto sus metricas quedarian
# expuestas a internet). El infra/app del gateway lo lee del remote state.
resource "random_password" "metrics_token" {
  length  = 48
  special = false
}

resource "random_password" "grafana_admin" {
  length  = 24
  special = false
}

resource "azurerm_container_app" "prometheus" {
  name                         = "${var.project}-prometheus"
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  revision_mode                = "Single"

  secret {
    name  = "metrics-token"
    value = random_password.metrics_token.result
  }

  template {
    # Instancia unica: el TSDB es local al contenedor.
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "prometheus"
      image  = "prom/prometheus:v3.5.0"
      cpu    = 0.25
      memory = "0.5Gi"

      # args reemplaza el CMD completo de la imagen: incluir el tsdb.path.
      args = [
        "--config.file=/puckzone-cfg/prometheus.yml",
        "--storage.tsdb.path=/prometheus",
        "--storage.tsdb.retention.time=3d",
      ]

      volume_mounts {
        name = "cfg"
        path = "/puckzone-cfg"
      }

      # Volumen de secrets: cada secret de la app aparece como un archivo con
      # su nombre; aqui el token que el scrape del gateway manda como Bearer.
      volume_mounts {
        name = "prom-secrets"
        path = "/etc/prom-secrets"
      }
    }

    volume {
      name         = "cfg"
      storage_type = "AzureFile"
      storage_name = azurerm_container_app_environment_storage.monitoring.name
    }

    volume {
      name         = "prom-secrets"
      storage_type = "Secret"
    }
  }

  # Interno: solo Grafana (y las apps del environment) lo alcanzan.
  # allow_insecure como en el resto de apps internas (sin eso ACA fuerza 308
  # a HTTPS con cert .internal. que los clientes rechazan).
  ingress {
    external_enabled           = false
    transport                  = "http"
    target_port                = 9090
    allow_insecure_connections = true

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }
}

resource "azurerm_container_app" "grafana" {
  name                         = "${var.project}-grafana"
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  revision_mode                = "Single"

  secret {
    name  = "grafana-admin-password"
    value = random_password.grafana_admin.result
  }

  template {
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "grafana"
      image  = "grafana/grafana:11.6.0"
      cpu    = 0.25
      memory = "0.5Gi"

      env {
        name  = "GF_SECURITY_ADMIN_USER"
        value = "admin"
      }
      env {
        name        = "GF_SECURITY_ADMIN_PASSWORD"
        secret_name = "grafana-admin-password"
      }
      env {
        name  = "GF_PATHS_PROVISIONING"
        value = "/puckzone-cfg/grafana/provisioning"
      }
      env {
        name  = "GF_USERS_ALLOW_SIGN_UP"
        value = "false"
      }
      env {
        name  = "GF_AUTH_ANONYMOUS_ENABLED"
        value = "false"
      }

      volume_mounts {
        name = "cfg"
        path = "/puckzone-cfg"
      }
    }

    volume {
      name         = "cfg"
      storage_type = "AzureFile"
      storage_name = azurerm_container_app_environment_storage.monitoring.name
    }
  }

  # Publica (con login admin): es la grafica que se muestra en clase.
  ingress {
    external_enabled = true
    transport        = "http"
    target_port      = 3000

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }
}
