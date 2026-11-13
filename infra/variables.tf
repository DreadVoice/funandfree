variable "project_id" {
  type    = string
  default = "standard-tools"
}

variable "project_number" {
  type    = string
  default = "124543570033"
}

variable "region" {
  type    = string
  default = "us-central1"
}

variable "zone" {
  type    = string
  default = "us-central1-a"
}

variable "instance_name" {
  type    = string
  default = "dread-projects"
}

variable "machine_type" {
  type    = string
  default = "e2-micro"
}

variable "boot_disk_size_gb" {
  type    = number
  default = 30
}

variable "boot_disk_type" {
  type    = string
  default = "pd-standard"
}

variable "boot_image" {
  type    = string
  default = "debian-cloud/debian-12-bookworm-v20261014"
}

variable "network_tag" {
  type    = string
  default = "app-server"
}

variable "releases_bucket" {
  type    = string
  default = "standard-tools-releases"
}

variable "release_retention_days" {
  type    = number
  default = 90
}

variable "github_repository" {
  type    = string
  default = "DreadVoice/funandfree"
}

variable "resend_secret_name" {
  type    = string
  default = "resend-api-key"
}

variable "billing_account" {
  type = string
}

variable "budget_amount" {
  type    = string
  default = "100"
}

variable "budget_currency" {
  type    = string
  default = "INR"
}

variable "budget_id" {
  type    = string
  default = ""
}
