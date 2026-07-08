output "game_internal_fqdn" {
  description = "FQDN interno de game; el gateway lo usa como GAME_SERVICE_URL"
  value       = "https://${azurerm_container_app.game.name}.internal.${data.terraform_remote_state.base.outputs.container_app_environment_default_domain}"
}
