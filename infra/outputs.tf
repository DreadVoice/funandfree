output "instance_ipv6" {
  value = google_compute_instance.app.network_interface[0].ipv6_access_config[0].external_ipv6
}

output "releases_bucket" {
  value = google_storage_bucket.releases.name
}

output "workload_identity_provider" {
  value = google_iam_workload_identity_pool_provider.github_oidc.name
}

output "github_ci_service_account" {
  value = google_service_account.github_ci.email
}

output "vm_runtime_service_account" {
  value = google_service_account.vm_runtime.email
}
