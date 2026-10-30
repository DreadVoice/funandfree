#!/bin/sh
set -eu

BINARY="${APP_BINARY:-/opt/event-platform/current/event-platform}"
PROJECT="${GCP_PROJECT:-standard-tools}"
SECRETS="${APP_SECRETS:-}"
METADATA="http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"

access_token() {
  curl -s -m 5 -H 'Metadata-Flavor: Google' "${METADATA}" \
    | python3 -c 'import sys, json; print(json.load(sys.stdin)["access_token"])'
}

fetch_secret() {
  curl -s -m 10 -H "Authorization: Bearer $1" \
    "https://secretmanager.googleapis.com/v1/projects/${PROJECT}/secrets/$2/versions/latest:access" \
    | python3 -c 'import sys, json, base64
payload = json.load(sys.stdin).get("payload")
if payload and payload.get("data"):
    sys.stdout.write(base64.b64decode(payload["data"]).decode())'
}

if [ -n "${SECRETS}" ]; then
  TOKEN="$(access_token || true)"
  if [ -z "${TOKEN}" ]; then
    echo "run-with-secrets: no metadata token available, starting without secrets" >&2
  else
    for pair in ${SECRETS}; do
      VAR="${pair%%=*}"
      NAME="${pair#*=}"
      VALUE="$(fetch_secret "${TOKEN}" "${NAME}" || true)"
      if [ -n "${VALUE}" ]; then
        export "${VAR}=${VALUE}"
        echo "run-with-secrets: loaded ${VAR} from ${NAME}" >&2
      else
        echo "run-with-secrets: ${NAME} has no accessible version, leaving ${VAR} unset" >&2
      fi
    done
  fi
fi

exec "${BINARY}" "$@"
