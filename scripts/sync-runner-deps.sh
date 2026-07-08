#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKERFILE="$ROOT_DIR/docker/runner/Dockerfile"

maven_property() {
  local name="$1"
  mvn -q -f "$ROOT_DIR/pom.xml" help:evaluate -Dexpression="$name" -DforceStdout
}

mysql_connector_version="$(maven_property mysql.connector.version)"

if [[ ! "$mysql_connector_version" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
  echo "Invalid mysql.connector.version: $mysql_connector_version" >&2
  exit 1
fi

tmp_file="$(mktemp)"
sed -E "s/^(ARG MYSQL_CONNECTOR_VERSION=).*/\1${mysql_connector_version}/" "$DOCKERFILE" > "$tmp_file"
mv "$tmp_file" "$DOCKERFILE"
