# Infraestructura PuckZone (Terraform)

La plataforma usa **Terraform distribuido**: la infraestructura compartida vive en
`infra/base/` de **este repo** (puckzone-game); cada repo de servicio tiene un
`infra/app/` que crea solo su Container App y lee los recursos compartidos via
remote state.

```
infra/base   (solo en puckzone-game)      infra/app   (en cada repo)
─────────────────────────────────────     ────────────────────────────────
resource group  puckzone-rg               Container App del servicio
Log Analytics + Application Insights        - lee base.tfstate (remote state)
Container Apps Environment                  - imagen desde ghcr.io
Postgres Flexible Server                    - env vars / secrets del servicio
  (auth_db, ranking_db, game_db)
Redis (Container App interna)
```

## State remoto

Todos los states viven en el Storage Account `puckzonetfstate`
(RG `puckzone-tfstate-rg`, contenedor `tfstate`), separado de `puckzone-rg`
para que un `terraform destroy` de la plataforma nunca borre el state.

| Stack | Key del state |
|---|---|
| `infra/base` | `base.tfstate` |
| game `infra/app` | `game.tfstate` |
| auth / matchmaking / ranking / gateway | `auth.tfstate` / `matchmaking.tfstate` / ... |

### Bootstrap (ya ejecutado el 2026-07-07, NO repetir)

El storage del state es lo unico que no se crea con Terraform (huevo-gallina):

```powershell
az group create --name puckzone-tfstate-rg --location eastus
az storage account create --name puckzonetfstate --resource-group puckzone-tfstate-rg `
  --location eastus --sku Standard_LRS --kind StorageV2 `
  --min-tls-version TLS1_2 --allow-blob-public-access false
az storage container create --name tfstate --account-name puckzonetfstate --auth-mode login
```

## Uso

Requisitos: Terraform >= 1.9, Azure CLI logueado (`az login`) en la suscripcion
Azure for Students.

```powershell
cd infra/base      # o infra/app
terraform init     # una vez (descarga providers y conecta el backend)
terraform plan     # revisar SIEMPRE antes de aplicar
terraform apply
```

Orden: `base` primero; los `app` de cada servicio despues (dependen de sus outputs).

## Decisiones (2026-07-07)

- **1 Postgres Flexible Server B1ms** con una database por servicio, en vez de un
  servidor por servicio: aislamiento logico suficiente y ~1/3 del costo.
- **Postgres en `centralus`** (el resto en `eastus`): Azure for Students tiene
  `LocationIsOfferRestricted` en eastus/eastus2 para Flexible Server. Sondear regiones
  con `az postgres flexible-server list-skus --location <region>`. Ojo: un intento
  fallido tombstonea nombre+region en ARM (~horas), por eso el nombre lleva la region.
- **Redis como Container App** interna (TCP 6379, sin ingress externo) en vez de
  Azure Cache for Redis: game solo guarda snapshots con TTL 1h, no necesita
  durabilidad. Sin contrasena porque solo es alcanzable dentro del environment.
- **Contrasena de Postgres** generada por `random_password`, vive solo en el state.
  Recuperarla: `terraform output -raw postgres_admin_password` (en infra/base).
- El pipeline CI/CD sigue siendo dueno del **deploy de imagenes** (build + push a
  GHCR y update de la Container App); Terraform solo administra la infraestructura.
