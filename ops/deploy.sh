#!/bin/sh
set -eu

BUCKET="${BUCKET:-standard-tools-releases}"
APP_ROOT="${APP_ROOT:-/opt/event-platform}"
APP_HOME="${APP_HOME:-/var/lib/event-platform}"
SERVICE="${SERVICE:-event-platform}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-60}"
KEEP_RELEASES="${KEEP_RELEASES:-3}"

if [ "$(id -u)" -ne 0 ]; then
  echo "run with sudo" >&2
  exit 1
fi

SHA="${1:-}"
if [ -z "${SHA}" ]; then
  SHA="$(gcloud storage ls --long "gs://${BUCKET}/releases/*/event-platform" 2>/dev/null \
    | awk 'NF >= 3 && $3 ~ /^gs:/ { print $2, $3 }' \
    | sort \
    | tail -1 \
    | awk '{ print $2 }' \
    | sed 's|.*/releases/||; s|/event-platform$||')"
fi
if [ -z "${SHA}" ]; then
  echo "no release given and none found in gs://${BUCKET}/releases/" >&2
  exit 1
fi

TARGET="${APP_ROOT}/releases/${SHA}"
PREVIOUS=""
if [ -L "${APP_ROOT}/current" ]; then
  PREVIOUS="$(readlink -f "${APP_ROOT}/current")"
fi

echo "deploying ${SHA}"

if [ ! -x "${TARGET}/event-platform" ]; then
  STAGE="$(mktemp -d)"
  trap 'rm -rf "${STAGE}"' EXIT
  gcloud storage cp "gs://${BUCKET}/releases/${SHA}/event-platform" "${STAGE}/event-platform" >/dev/null
  gcloud storage cp "gs://${BUCKET}/releases/${SHA}/event-platform.sha256" "${STAGE}/event-platform.sha256" >/dev/null

  EXPECTED="$(tr -d '[:space:]' < "${STAGE}/event-platform.sha256")"
  ACTUAL="$(sha256sum "${STAGE}/event-platform" | awk '{print $1}')"
  if [ "${EXPECTED}" != "${ACTUAL}" ]; then
    echo "checksum mismatch for ${SHA}" >&2
    echo "  expected ${EXPECTED}" >&2
    echo "  actual   ${ACTUAL}" >&2
    exit 1
  fi
  echo "checksum verified"

  mkdir -p "${TARGET}"
  install -m 755 -o root -g root "${STAGE}/event-platform" "${TARGET}/event-platform"
fi

DB="${APP_HOME}/data/events.db"
if [ -f "${DB}" ]; then
  STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
  BACKUP="${APP_HOME}/backups/events-${STAMP}.db"
  sqlite3 "${DB}" ".backup '${BACKUP}'"
  gzip -f "${BACKUP}"
  echo "database backed up to ${BACKUP}.gz"
fi

ln -sfn "${TARGET}" "${APP_ROOT}/current.new"
mv -Tf "${APP_ROOT}/current.new" "${APP_ROOT}/current"
systemctl restart "${SERVICE}"

echo "waiting for health"
ELAPSED=0
HEALTHY=0
while [ "${ELAPSED}" -lt "${HEALTH_TIMEOUT}" ]; do
  if curl -fsS -m 3 "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
    HEALTHY=1
    break
  fi
  sleep 2
  ELAPSED=$((ELAPSED + 2))
done

if [ "${HEALTHY}" -ne 1 ]; then
  echo "health check failed after ${HEALTH_TIMEOUT}s" >&2
  journalctl -u "${SERVICE}" -n 30 --no-pager >&2 || true

  if [ -z "${PREVIOUS}" ] || [ ! -x "${PREVIOUS}/event-platform" ]; then
    echo "no previous release to roll back to; service is down" >&2
    exit 1
  fi

  echo "rolling back to $(basename "${PREVIOUS}")" >&2
  ln -sfn "${PREVIOUS}" "${APP_ROOT}/current.new"
  mv -Tf "${APP_ROOT}/current.new" "${APP_ROOT}/current"
  systemctl restart "${SERVICE}"

  ELAPSED=0
  while [ "${ELAPSED}" -lt "${HEALTH_TIMEOUT}" ]; do
    if curl -fsS -m 3 "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "rolled back to $(basename "${PREVIOUS}") and healthy" >&2
      exit 1
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
  done

  echo "ROLLBACK FAILED: $(basename "${PREVIOUS}") is not healthy either; service is down" >&2
  exit 2
fi

echo "healthy on ${SHA}"

cd "${APP_ROOT}/releases"
ls -1t | tail -n "+$((KEEP_RELEASES + 1))" | while read -r old; do
  if [ "${APP_ROOT}/releases/${old}" != "$(readlink -f "${APP_ROOT}/current")" ]; then
    rm -rf "${APP_ROOT}/releases/${old}"
  fi
done

systemctl is-active "${SERVICE}"
