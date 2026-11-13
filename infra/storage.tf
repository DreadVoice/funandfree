resource "google_storage_bucket" "releases" {
  name                        = var.releases_bucket
  location                    = upper(var.region)
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  force_destroy               = false

  lifecycle_rule {
    action {
      type = "Delete"
    }

    condition {
      age = var.release_retention_days
    }
  }

  soft_delete_policy {
    retention_duration_seconds = 604800
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "google_storage_bucket_iam_member" "github_ci_create" {
  bucket = google_storage_bucket.releases.name
  role   = "roles/storage.objectCreator"
  member = "serviceAccount:${google_service_account.github_ci.email}"
}

resource "google_storage_bucket_iam_member" "github_ci_view" {
  bucket = google_storage_bucket.releases.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.github_ci.email}"
}
