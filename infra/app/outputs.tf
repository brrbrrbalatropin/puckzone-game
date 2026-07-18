output "game_internal_fqdn" {
  description = "FQDN interno del shard 0; el gateway lo usa como GAME_SERVICE_URL"
  value       = "https://${azurerm_container_app.game["0"].name}.internal.${data.terraform_remote_state.base.outputs.container_app_environment_default_domain}"
}

output "game_shard_urls" {
  description = "URL interna de cada shard, en orden: GAME_SHARD_URLS de matchmaking y rutas /ws-{shard} del gateway"
  value       = [for key in sort(keys(local.game_shards)) : "http://${local.game_shards[key]}"]
}
