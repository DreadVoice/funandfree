terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.0"
    }
  }

  backend "gcs" {
    bucket = "standard-tools-tfstate"
    prefix = "funandfree"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone

  billing_project       = var.project_id
  user_project_override = true
}
