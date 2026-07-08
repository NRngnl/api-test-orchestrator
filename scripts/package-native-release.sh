#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <native-binary> <release-tag> <output-dir>" >&2
  exit 64
fi

binary_path="$1"
tag="$2"
output_dir="$3"
version="${tag#v}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

if [ -z "${version}" ] || [ "${version}" = "${tag}" ]; then
  echo "Release tag must start with v, for example v0.1.0." >&2
  exit 64
fi

if [ ! -x "${binary_path}" ]; then
  echo "Native binary is missing or not executable: ${binary_path}" >&2
  exit 66
fi

connector_version="9.2.0"
protobuf_version="4.29.0"
protobuf_license_source="${repo_root}/licenses/protobuf-java/LICENSE"

if [ -n "${MAVEN_REPO_LOCAL:-}" ]; then
  maven_repo_local="${MAVEN_REPO_LOCAL}"
else
  maven_repo_local="$(mvn -q -DforceStdout help:evaluate -Dexpression=settings.localRepository)"
fi

if [ -z "${maven_repo_local}" ]; then
  echo "Unable to resolve Maven local repository." >&2
  exit 66
fi

connector_jar="${maven_repo_local}/com/mysql/mysql-connector-j/${connector_version}/mysql-connector-j-${connector_version}.jar"
protobuf_jar="${maven_repo_local}/com/google/protobuf/protobuf-java/${protobuf_version}/protobuf-java-${protobuf_version}.jar"

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

archive_base="api-test-orchestrator-${tag}-linux-x64"
staging_dir="${output_dir}/${archive_base}"
connector_license_dir="${staging_dir}/licenses/mysql-connector-j"
protobuf_license_dir="${staging_dir}/licenses/protobuf-java"

rm -rf "${staging_dir}"
mkdir -p "${connector_license_dir}" "${protobuf_license_dir}"

cp "${binary_path}" "${staging_dir}/api-test-orchestrator"
cp LICENSE NOTICE THIRD-PARTY.txt "${staging_dir}/"

unzip -p "${connector_jar}" LICENSE > "${connector_license_dir}/LICENSE"
unzip -p "${connector_jar}" README > "${connector_license_dir}/README"
unzip -p "${connector_jar}" INFO_BIN > "${connector_license_dir}/INFO_BIN"
unzip -p "${connector_jar}" INFO_SRC > "${connector_license_dir}/INFO_SRC"
cp "${protobuf_license_source}" "${protobuf_license_dir}/LICENSE"

tar -C "${output_dir}" -czf "${output_dir}/${archive_base}.tar.gz" "${archive_base}"
(
  cd "${output_dir}"
  sha256sum "${archive_base}.tar.gz" > "${archive_base}.tar.gz.sha256"
)
