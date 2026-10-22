#!/bin/sh
set -eu

PROJECT_ID="${PROJECT_ID:-funandfree}"
REGION="${REGION:-us-central1}"
GITHUB_REPO="${GITHUB_REPO:-DreadVoice/funandfree}"
POOL="${POOL:-github}"
PROVIDER="${PROVIDER:-github-oidc}"
SA_NAME="${SA_NAME:-github-ci}"
BUCKET="${BUCKET:-${PROJECT_ID}-releases}"
RETENTION_DAYS="${RETENTION_DAYS:-90}"

SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
POOL_NAME="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}"

gcloud services enable \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  iam.googleapis.com \
  storage.googleapis.com \
  --project "${PROJECT_ID}"

if ! gcloud iam workload-identity-pools describe "${POOL}" \
      --project "${PROJECT_ID}" --location global >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "${POOL}" \
    --project "${PROJECT_ID}" \
    --location global \
    --display-name "GitHub Actions"
fi

if ! gcloud iam workload-identity-pools providers describe "${PROVIDER}" \
      --project "${PROJECT_ID}" --location global --workload-identity-pool "${POOL}" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers create-oidc "${PROVIDER}" \
    --project "${PROJECT_ID}" \
    --location global \
    --workload-identity-pool "${POOL}" \
    --display-name "GitHub OIDC" \
    --issuer-uri "https://token.actions.githubusercontent.com" \
    --attribute-mapping "google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
    --attribute-condition "assertion.repository == '${GITHUB_REPO}'"
fi

if ! gcloud iam service-accounts describe "${SA_EMAIL}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud iam service-accounts create "${SA_NAME}" \
    --project "${PROJECT_ID}" \
    --display-name "GitHub Actions release publisher"
fi

gcloud iam service-accounts add-iam-policy-binding "${SA_EMAIL}" \
  --project "${PROJECT_ID}" \
  --role roles/iam.workloadIdentityUser \
  --member "principalSet://iam.googleapis.com/${POOL_NAME}/attribute.repository/${GITHUB_REPO}"

if ! gcloud storage buckets describe "gs://${BUCKET}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud storage buckets create "gs://${BUCKET}" \
    --project "${PROJECT_ID}" \
    --location "${REGION}" \
    --uniform-bucket-level-access \
    --public-access-prevention
fi

LIFECYCLE_FILE="$(mktemp)"
cat > "${LIFECYCLE_FILE}" <<JSON
{
  "rule": [
    {
      "action": { "type": "Delete" },
      "condition": { "age": ${RETENTION_DAYS} }
    }
  ]
}
JSON
gcloud storage buckets update "gs://${BUCKET}" --lifecycle-file="${LIFECYCLE_FILE}"
rm -f "${LIFECYCLE_FILE}"

gcloud storage buckets add-iam-policy-binding "gs://${BUCKET}" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role roles/storage.objectCreator

echo
echo "Add these GitHub repository secrets to ${GITHUB_REPO}:"
echo "  GCP_WIF_PROVIDER        = ${POOL_NAME}/providers/${PROVIDER}"
echo "  GCP_CI_SERVICE_ACCOUNT  = ${SA_EMAIL}"
echo "  GCP_RELEASE_BUCKET      = ${BUCKET}"
echo
echo "The provider only accepts tokens from ${GITHUB_REPO}."
echo "The service account can create objects in gs://${BUCKET} but cannot read, overwrite or delete them."
echo "Objects are deleted automatically after ${RETENTION_DAYS} days."
