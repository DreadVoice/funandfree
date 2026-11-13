resource "google_secret_manager_secret" "resend_api_key" {
  secret_id = var.resend_secret_name

  replication {
    auto {}
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_secret_manager_secret_iam_member" "vm_runtime_accessor" {
  secret_id = google_secret_manager_secret.resend_api_key.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.vm_runtime.email}"
}
