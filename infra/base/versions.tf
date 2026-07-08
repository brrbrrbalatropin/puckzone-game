terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State remoto compartido de la plataforma. El storage se creó una única vez
  # con az cli (ver infra/README.md); cada repo de servicio usa este mismo
  # storage con su propio key (game.tfstate, auth.tfstate, ...).
  backend "azurerm" {
    resource_group_name  = "puckzone-tfstate-rg"
    storage_account_name = "puckzonetfstate"
    container_name       = "tfstate"
    key                  = "base.tfstate"
  }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}
