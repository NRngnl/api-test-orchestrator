#!/usr/bin/env bash
set -euo pipefail

truthy() {
  case "${1:-}" in
    1 | true | TRUE | yes | YES | on | ON)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

cert_path="${ATO_MOCK_SSL_CERT_PATH:-}"
key_path="${ATO_MOCK_SSL_KEY_PATH:-}"

if truthy "${ATO_MOCK_SSL_GENERATE:-false}"; then
  if [ -z "$cert_path" ] || [ -z "$key_path" ]; then
    echo "ATO_MOCK_SSL_GENERATE requires ATO_MOCK_SSL_CERT_PATH and ATO_MOCK_SSL_KEY_PATH" >&2
    exit 1
  fi

  if [ ! -s "$cert_path" ] || [ ! -s "$key_path" ]; then
    mkdir -p "$(dirname "$cert_path")" "$(dirname "$key_path")"
    openssl req -x509 -nodes -days "${ATO_MOCK_SSL_DAYS:-365}" \
      -newkey rsa:2048 \
      -subj "${ATO_MOCK_SSL_SUBJECT:-/CN=localhost}" \
      -keyout "$key_path" \
      -out "$cert_path" \
      -addext "subjectAltName = ${ATO_MOCK_SSL_SAN:-DNS:localhost}" >/dev/null 2>&1
  fi
fi

if truthy "${ATO_TRUST_MOCK_SSL_CERT:-true}" && [ -n "$cert_path" ] && [ -s "$cert_path" ]; then
  cp "$cert_path" /etc/pki/ca-trust/source/anchors/ato-mock.crt
  update-ca-trust extract
fi

# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-} \
  -cp "${ATO_CLASSPATH:-/app/api-test-orchestrator.jar:/app/lib/*:/mocks:/shareutils:/app}" \
  io.vtz.apitest.interfaces.cli.ApiTestOrchestratorCli "$@"
