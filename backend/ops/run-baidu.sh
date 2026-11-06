#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$BACKEND/.env"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

export TEACHTRACK_STORAGE_PROVIDER="${TEACHTRACK_STORAGE_PROVIDER:-baidu-pan}"
export TEACHTRACK_BYPY="${TEACHTRACK_BYPY:-bypy}"
export TEACHTRACK_BAIDU_ROOT="${TEACHTRACK_BAIDU_ROOT:-teachtrack}"
export JAVA_HOME="${JAVA_HOME:-D:/SoftWare/jdk-21}"

cd "$ROOT"
exec ./gradlew.bat -p "$BACKEND" bootRun --args="--spring.profiles.active=baidu"
