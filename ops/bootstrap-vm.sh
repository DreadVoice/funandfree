#!/bin/sh
set -eu

APP_USER="${APP_USER:-appsvc}"
APP_HOME="${APP_HOME:-/var/lib/event-platform}"
APP_ROOT="${APP_ROOT:-/opt/event-platform}"
SWAP_SIZE="${SWAP_SIZE:-2G}"
SWAP_FILE="${SWAP_FILE:-/swapfile}"
ENV_FILE="${ENV_FILE:-/etc/event-platform.env}"

if [ "$(id -u)" -ne 0 ]; then
  echo "run with sudo" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg sqlite3 debian-keyring debian-archive-keyring apt-transport-https

if [ ! -f "${SWAP_FILE}" ]; then
  fallocate -l "${SWAP_SIZE}" "${SWAP_FILE}"
  chmod 600 "${SWAP_FILE}"
  mkswap "${SWAP_FILE}" >/dev/null
  swapon "${SWAP_FILE}"
  if ! grep -q "^${SWAP_FILE}" /etc/fstab; then
    echo "${SWAP_FILE} none swap sw 0 0" >> /etc/fstab
  fi
fi

if ! id "${APP_USER}" >/dev/null 2>&1; then
  useradd --system --home-dir "${APP_HOME}" --create-home --shell /usr/sbin/nologin "${APP_USER}"
fi

mkdir -p "${APP_ROOT}/releases" "${APP_HOME}/data" "${APP_HOME}/backups"
chown -R "${APP_USER}:${APP_USER}" "${APP_HOME}"
chown -R root:root "${APP_ROOT}"

if [ ! -f "${ENV_FILE}" ]; then
  cat > "${ENV_FILE}" <<ENV
FIREBASE_PROJECT_ID=funandfree
APP_DB_PATH=${APP_HOME}/data/events.db
APP_ALLOWED_ORIGINS=http://localhost:5173
APP_SCHEDULER_SA=
APP_OIDC_AUDIENCE=
RESEND_API_KEY=
ENV
  chmod 600 "${ENV_FILE}"
  chown root:root "${ENV_FILE}"
fi

if ! command -v caddy >/dev/null 2>&1; then
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key \
    | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt \
    | tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null
  apt-get update -qq
  apt-get install -y -qq caddy
fi

install -m 644 -o root -g root "$(dirname "$0")/event-platform.service" /etc/systemd/system/event-platform.service
install -m 644 -o root -g root "$(dirname "$0")/Caddyfile" /etc/caddy/Caddyfile

systemctl daemon-reload
systemctl enable event-platform >/dev/null 2>&1 || true

caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
systemctl reload caddy 2>/dev/null || systemctl restart caddy

echo "bootstrap complete"
echo "  app user   : ${APP_USER}"
echo "  releases   : ${APP_ROOT}/releases"
echo "  data       : ${APP_HOME}/data"
echo "  env file   : ${ENV_FILE} (edit before first deploy)"
echo "  swap       : $(swapon --show=NAME,SIZE --noheadings | tr '\n' ' ')"
