#!/bin/sh
set -eu

PROJECT_ID="${PROJECT_ID:-standard-tools}"
REGION="${REGION:-us-central1}"
API_BASE_URL="${API_BASE_URL:?set API_BASE_URL, for example https://api.example.com}"
JOB_NAME="${JOB_NAME:-event-reminders}"
SCHEDULE="${SCHEDULE:-0 * * * *}"
TIME_ZONE="${TIME_ZONE:-Etc/UTC}"
SA_NAME="${SA_NAME:-scheduler-invoker}"
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
TARGET_URL="${API_BASE_URL}/internal/jobs/reminders"

gcloud services enable cloudscheduler.googleapis.com --project "${PROJECT_ID}"

if ! gcloud iam service-accounts describe "${SA_EMAIL}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud iam service-accounts create "${SA_NAME}" \
    --project "${PROJECT_ID}" \
    --display-name "Cloud Scheduler reminder invoker"
fi

if gcloud scheduler jobs describe "${JOB_NAME}" --location "${REGION}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  ACTION=update
else
  ACTION=create
fi

gcloud scheduler jobs "${ACTION}" http "${JOB_NAME}" \
  --project "${PROJECT_ID}" \
  --location "${REGION}" \
  --schedule "${SCHEDULE}" \
  --time-zone "${TIME_ZONE}" \
  --uri "${TARGET_URL}" \
  --http-method POST \
  --oidc-service-account-email "${SA_EMAIL}" \
  --oidc-token-audience "${TARGET_URL}" \
  --attempt-deadline 300s \
  --max-retry-attempts 3 \
  --min-backoff 30s \
  --max-backoff 300s

echo
echo "Set these on the VM service before the job runs:"
echo "  APP_SCHEDULER_SA=${SA_EMAIL}"
echo "  APP_OIDC_AUDIENCE=${TARGET_URL}"
echo
echo "Trigger once by hand with:"
echo "  gcloud scheduler jobs run ${JOB_NAME} --location ${REGION} --project ${PROJECT_ID}"
