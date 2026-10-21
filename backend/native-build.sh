#!/bin/sh
set -e

IMAGE="${GRAALVM_IMAGE:-ghcr.io/graalvm/native-image-community:21}"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

docker run --rm \
  -v "${PROJECT_DIR}:/app" \
  -v "${HOME}/.m2:/root/.m2" \
  -w /app \
  -e FIREBASE_PROJECT_ID="${FIREBASE_PROJECT_ID:-aot-build-placeholder}" \
  --entrypoint sh \
  "${IMAGE}" \
  -c './mvnw -B -ntp -DskipTests -Pnative native:compile'
