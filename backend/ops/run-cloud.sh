#!/usr/bin/env bash
set -euo pipefail

# Cloud profile launcher for Git Bash.
# Loads backend/.env without echoing secrets, then starts Spring Boot.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$BACKEND/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "backend/.env not found. Copy .env.example to .env and fill the cloud values." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

export JAVA_HOME="${JAVA_HOME:-D:/SoftWare/jdk-21}"
cd "$ROOT"
exec ./gradlew.bat -p "$BACKEND" bootRun --args="--spring.profiles.active=cloud"
