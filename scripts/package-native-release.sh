#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
  echo "Usage: $0 <native-binary> <release-tag> <output-dir> [platform]" >&2
  exit 64
fi

binary_path="$1"
tag="$2"
output_dir="$3"
platform="${4:-linux-x64}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

if [[ ! "${tag}" =~ ^v ]]; then
  echo "Release tag must start with v, for example v0.1.0." >&2
  exit 64
fi

if [[ ! "${tag}" =~ ^v[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
  echo "Release tag contains unsafe archive characters: ${tag}" >&2
  exit 64
fi

if [[ ! "${platform}" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
  echo "Platform contains unsafe archive characters: ${platform}" >&2
  exit 64
fi

if [ ! -x "${binary_path}" ]; then
  echo "Native binary is missing or not executable: ${binary_path}" >&2
  exit 66
fi

protobuf_license_source="${repo_root}/licenses/protobuf-java/LICENSE"
maven_args=(-q -f "${repo_root}/pom.xml")

if [ -n "${MAVEN_REPO_LOCAL:-}" ]; then
  maven_args+=("-Dmaven.repo.local=${MAVEN_REPO_LOCAL}")
fi

connector_version="$(mvn "${maven_args[@]}" -DforceStdout help:evaluate -Dexpression=mysql.connector.version)"

if [[ ! "${connector_version}" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
  echo "Invalid mysql.connector.version: ${connector_version}" >&2
  exit 66
fi

classpath_file="$(mktemp)"
trap 'rm -f "${classpath_file}"' EXIT

mvn "${maven_args[@]}" "-Dmdep.outputFile=${classpath_file}" dependency:build-classpath >/dev/null

classpath_jar() {
  local pattern="$1"
  local entry
  while IFS= read -r entry; do
    case "$entry" in
      *"$pattern"*)
        printf '%s\n' "$entry"
        return 0
        ;;
    esac
  done < <(tr ':' '\n' < "$classpath_file")
  return 1
}

if ! connector_jar="$(classpath_jar "/com/mysql/mysql-connector-j/${connector_version}/mysql-connector-j-${connector_version}.jar")"; then
  echo "MySQL Connector/J artifact is missing from the resolved Maven classpath." >&2
  echo "Run mvn -B -DskipTests dependency:go-offline first." >&2
  exit 66
fi

if ! protobuf_jar="$(classpath_jar "/com/google/protobuf/protobuf-java/")"; then
  echo "protobuf-java artifact is missing from the resolved Maven classpath." >&2
  echo "Run mvn -B -DskipTests dependency:go-offline first." >&2
  exit 66
fi

if [ ! -f "${connector_jar}" ]; then
  echo "MySQL Connector/J artifact is missing: ${connector_jar}" >&2
  echo "Run mvn -B -DskipTests dependency:go-offline first." >&2
  exit 66
fi

if [ ! -f "${protobuf_jar}" ]; then
  echo "protobuf-java artifact is missing: ${protobuf_jar}" >&2
  echo "Run mvn -B -DskipTests dependency:go-offline first." >&2
  exit 66
fi

if [ ! -f "${protobuf_license_source}" ]; then
  echo "protobuf-java license file is missing: ${protobuf_license_source}" >&2
  exit 66
fi

mkdir -p "${output_dir}"
output_root="$(cd "${output_dir}" && pwd -P)"
archive_base="api-test-orchestrator-${tag}-${platform}"
staging_dir="${output_root}/${archive_base}"
case "${staging_dir}" in
  "${output_root}"/*)
    ;;
  *)
    echo "Resolved staging directory escapes output directory: ${staging_dir}" >&2
    exit 66
    ;;
esac
connector_license_dir="${staging_dir}/licenses/mysql-connector-j"
protobuf_license_dir="${staging_dir}/licenses/protobuf-java"

rm -rf "${staging_dir}"
mkdir -p "${connector_license_dir}" "${protobuf_license_dir}"

cp "${binary_path}" "${staging_dir}/api-test-orchestrator"
cp "${repo_root}/LICENSE" "${repo_root}/NOTICE" "${repo_root}/THIRD-PARTY.txt" "${staging_dir}/"

unzip -p "${connector_jar}" LICENSE > "${connector_license_dir}/LICENSE"
unzip -p "${connector_jar}" README > "${connector_license_dir}/README"
unzip -p "${connector_jar}" INFO_BIN > "${connector_license_dir}/INFO_BIN"
unzip -p "${connector_jar}" INFO_SRC > "${connector_license_dir}/INFO_SRC"
cp "${protobuf_license_source}" "${protobuf_license_dir}/LICENSE"

tar -C "${output_root}" -czf "${output_root}/${archive_base}.tar.gz" "${archive_base}"
(
  cd "${output_root}"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${archive_base}.tar.gz" > "${archive_base}.tar.gz.sha256"
  else
    shasum -a 256 "${archive_base}.tar.gz" > "${archive_base}.tar.gz.sha256"
  fi
)
