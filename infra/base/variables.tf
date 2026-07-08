# El subscription id no es un secreto (es un identificador, no una credencial),
# por eso puede vivir como default en el repo de un proyecto academico.
variable "subscription_id" {
  description = "Suscripcion de Azure donde se despliega la plataforma"
  type        = string
  default     = "d5860455-eb5f-4995-a11c-8be250730e90"
}

variable "location" {
  description = "Region de Azure para todos los recursos"
  type        = string
  default     = "eastus"
}

variable "project" {
  description = "Prefijo de nombres para todos los recursos"
  type        = string
  default     = "puckzone"
}

variable "postgres_admin_login" {
  description = "Usuario administrador del Postgres Flexible Server"
  type        = string
  default     = "puckzoneadmin"
}
