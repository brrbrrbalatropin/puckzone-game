# Secreto JWT de produccion, compartido por toda la plataforma: auth lo usa para
# FIRMAR tokens; gateway y los demas servicios lo usan para VALIDAR localmente.
# Vive solo en el state (storage privado); cada infra/app lo lee via remote state
# y lo inyecta como secret de su Container App (PUCKZONE_JWT_SECRET).
# 64 bytes => jjwt firma HS512; los consumidores usan verifyWith (agnostico al alg).
resource "random_password" "jwt_secret" {
  length  = 64
  special = false # evita problemas de escape en env vars
}