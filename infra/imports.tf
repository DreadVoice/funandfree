import {
  to = google_compute_instance.app
  id = "projects/standard-tools/zones/us-central1-a/instances/dread-projects"
}

import {
  to = google_compute_firewall.ssh_from_iap
  id = "projects/standard-tools/global/firewalls/allow-ssh-from-iap"
}

import {
  to = google_compute_firewall.web_public
  id = "projects/standard-tools/global/firewalls/allow-web-public"
}

import {
  to = google_service_account.vm_runtime
  id = "projects/standard-tools/serviceAccounts/vm-runtime@standard-tools.iam.gserviceaccount.com"
}

import {
  to = google_service_account.github_ci
  id = "projects/standard-tools/serviceAccounts/github-ci@standard-tools.iam.gserviceaccount.com"
}

import {
  to = google_project_iam_member.vm_runtime_logging
  id = "standard-tools roles/logging.logWriter serviceAccount:vm-runtime@standard-tools.iam.gserviceaccount.com"
}

import {
  to = google_iam_workload_identity_pool.github
  id = "projects/standard-tools/locations/global/workloadIdentityPools/github"
}

import {
  to = google_iam_workload_identity_pool_provider.github_oidc
  id = "projects/standard-tools/locations/global/workloadIdentityPools/github/providers/github-oidc"
}

import {
  to = google_service_account_iam_member.github_ci_wif
  id = "projects/standard-tools/serviceAccounts/github-ci@standard-tools.iam.gserviceaccount.com roles/iam.workloadIdentityUser principalSet://iam.googleapis.com/projects/124543570033/locations/global/workloadIdentityPools/github/attribute.repository/DreadVoice/funandfree"
}

import {
  to = google_storage_bucket.releases
  id = "standard-tools/standard-tools-releases"
}

import {
  to = google_storage_bucket_iam_member.github_ci_create
  id = "b/standard-tools-releases roles/storage.objectCreator serviceAccount:github-ci@standard-tools.iam.gserviceaccount.com"
}

import {
  to = google_storage_bucket_iam_member.github_ci_view
  id = "b/standard-tools-releases roles/storage.objectViewer serviceAccount:github-ci@standard-tools.iam.gserviceaccount.com"
}

import {
  to = google_secret_manager_secret.resend_api_key
  id = "projects/standard-tools/secrets/resend-api-key"
}

import {
  to = google_secret_manager_secret_iam_member.vm_runtime_accessor
  id = "projects/standard-tools/secrets/resend-api-key roles/secretmanager.secretAccessor serviceAccount:vm-runtime@standard-tools.iam.gserviceaccount.com"
}

import {
  to = google_billing_budget.zero_cost_guard
  id = "billingAccounts/${var.billing_account}/budgets/${var.budget_id}"
}
