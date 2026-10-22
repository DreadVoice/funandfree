#!/bin/sh
set -e

IMAGE="${RUNTIME_IMAGE:-debian:12-slim}"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

docker run --rm \
  -v "${PROJECT_DIR}:/app" \
  -w /app \
  -e FIREBASE_PROJECT_ID=spike-project \
  -e APP_DB_PATH=/tmp/spike.db \
  "${IMAGE}" \
  /app/target/event-platform --spring.profiles.active=spike
