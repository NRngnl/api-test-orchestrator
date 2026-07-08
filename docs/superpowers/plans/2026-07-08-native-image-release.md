# Native Image Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Linux x64 GraalVM Native Image release asset that includes MySQL Connector/J while preserving the existing driver-neutral shaded JAR release.

**Architecture:** The Maven `native` profile builds a native executable directly from the project dependency graph, separate from the shaded JAR. A small packaging script assembles the native binary with project and Connector/J license metadata into a Linux x64 tarball, and the tag-driven GitHub Release workflow uploads both JAR and native assets.

**Tech Stack:** Java 21, Maven, GraalVM Native Build Tools, GitHub Actions, Bash, MySQL Connector/J 9.2.0, protobuf-java 4.29.0.

## Global Constraints

- Keep `pom.xml` project version at `0.1.0-SNAPSHOT`; release workflows derive `0.1.0` from tag `v0.1.0`.
- Keep `ApiTestOrchestratorCli` version at `0.1.0`.
- Keep the shaded JAR driver-neutral: it must continue to exclude `com.mysql:mysql-connector-j` and `com.google.protobuf:protobuf-java`.
- Add only Linux x64 native assets in this implementation.
- Native release includes MySQL Connector/J and protobuf as bundled components.
- Preserve project notices and Connector/J license/source metadata in the native tarball.
- Do not add macOS, Windows, arbitrary runtime JDBC driver loading, or MySQL integration-test containers.

---

## File Structure

- Modify `pom.xml`: add GraalVM native plugin version property and a `native` Maven profile.
- Create `scripts/package-native-release.sh`: package the native executable and required notice/license files into a tarball and checksum.
- Modify `.github/workflows/release.yml`: build, verify, package, and upload Linux x64 native assets in addition to the existing JAR assets.
- Modify `README.md`: document native release assets, runtime usage, and licensing boundary.
- Modify `THIRD-PARTY.txt`: identify components bundled only in the Linux x64 native archive.

---

### Task 1: Add Maven Native Profile

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Consumes: existing main class `io.vtz.apitest.interfaces.cli.ApiTestOrchestratorCli`.
- Produces: Maven command `mvn -B -Pnative -DskipTests package` builds `target/api-test-orchestrator`.

- [ ] **Step 1: Verify the native profile is currently absent**

Run:

```bash
mvn -B clean -Pnative -DskipTests package
test -x target/api-test-orchestrator
```

Expected: Maven may warn that profile `native` does not exist, and `test -x target/api-test-orchestrator` fails because no native executable is produced.

- [ ] **Step 2: Add the native plugin version property**

Modify the `<properties>` block in `pom.xml` so it contains this new property after `junit.version`:

```xml
        <native.maven.plugin.version>1.1.3</native.maven.plugin.version>
```

- [ ] **Step 3: Add the native Maven profile**

Append this `<profiles>` block after the closing `</build>` element and before `</project>`:

```xml
    <profiles>
        <profile>
            <id>native</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.graalvm.buildtools</groupId>
                        <artifactId>native-maven-plugin</artifactId>
                        <version>${native.maven.plugin.version}</version>
                        <extensions>true</extensions>
                        <executions>
                            <execution>
                                <id>build-native</id>
                                <phase>package</phase>
                                <goals>
                                    <goal>compile-no-fork</goal>
                                </goals>
                            </execution>
                        </executions>
                        <configuration>
                            <imageName>${project.artifactId}</imageName>
                            <mainClass>io.vtz.apitest.interfaces.cli.ApiTestOrchestratorCli</mainClass>
                            <fallback>false</fallback>
                            <buildArgs>
                                <buildArg>--enable-url-protocols=http,https</buildArg>
                                <buildArg>-H:+ReportExceptionStackTraces</buildArg>
                            </buildArgs>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
```

- [ ] **Step 4: Verify the JVM build still passes**

Run:

```bash
mvn -B test
```

Expected: `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Verify the native build produces the executable**

Run with GraalVM JDK 21 active:

```bash
mvn -B -Pnative -DskipTests package
test -x target/api-test-orchestrator
target/api-test-orchestrator --version
target/api-test-orchestrator --help >/tmp/api-test-orchestrator-native-help.txt
```

Expected:

```text
0.1.0
```

If the native build fails on missing reflection, resources, proxies, serialization, or class initialization, stop this task and use `superpowers:systematic-debugging`. Add only the smallest targeted native-image metadata or build argument that corresponds to the observed failure, then rerun this step and `mvn -B test`.

- [ ] **Step 6: Commit**

Run:

```bash
git add pom.xml
git commit -m "build(native): add GraalVM native profile"
```

---

### Task 2: Add Native Release Packaging Script

**Files:**
- Create: `scripts/package-native-release.sh`

**Interfaces:**
- Consumes: native binary path, release tag, output directory.
- Produces: `target/api-test-orchestrator-v0.1.0-linux-x64.tar.gz` and matching `.sha256` when called with `v0.1.0` and `target`.

- [ ] **Step 1: Verify the packaging command is currently missing**

Run:

```bash
scripts/package-native-release.sh target/api-test-orchestrator v0.1.0 target/native-package-test
```

Expected: shell fails with `No such file or directory`.

- [ ] **Step 2: Create `scripts/package-native-release.sh`**

Create the file with this exact content:

```bash
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

if [ -z "${version}" ] || [ "${version}" = "${tag}" ]; then
  echo "Release tag must start with v, for example v0.1.0." >&2
  exit 64
fi

if [ ! -x "${binary_path}" ]; then
  echo "Native binary is missing or not executable: ${binary_path}" >&2
  exit 66
fi

connector_version="9.2.0"
connector_jar="${HOME}/.m2/repository/com/mysql/mysql-connector-j/${connector_version}/mysql-connector-j-${connector_version}.jar"

if [ ! -f "${connector_jar}" ]; then
  echo "MySQL Connector/J artifact is missing: ${connector_jar}" >&2
  echo "Run mvn -B -DskipTests dependency:go-offline first." >&2
  exit 66
fi

archive_base="api-test-orchestrator-${tag}-linux-x64"
staging_dir="${output_dir}/${archive_base}"
license_dir="${staging_dir}/licenses/mysql-connector-j"

rm -rf "${staging_dir}"
mkdir -p "${license_dir}"

cp "${binary_path}" "${staging_dir}/api-test-orchestrator"
cp LICENSE NOTICE THIRD-PARTY.txt "${staging_dir}/"

unzip -p "${connector_jar}" LICENSE > "${license_dir}/LICENSE"
unzip -p "${connector_jar}" README > "${license_dir}/README"
unzip -p "${connector_jar}" INFO_BIN > "${license_dir}/INFO_BIN"
unzip -p "${connector_jar}" INFO_SRC > "${license_dir}/INFO_SRC"

tar -C "${output_dir}" -czf "${output_dir}/${archive_base}.tar.gz" "${archive_base}"
(
  cd "${output_dir}"
  sha256sum "${archive_base}.tar.gz" > "${archive_base}.tar.gz.sha256"
)
```

- [ ] **Step 3: Make the script executable**

Run:

```bash
chmod +x scripts/package-native-release.sh
```

- [ ] **Step 4: Verify packaging with a test executable**

Run:

```bash
mkdir -p target/native-package-test/bin
printf '#!/usr/bin/env sh\nprintf "0.1.0\\n"\n' > target/native-package-test/bin/api-test-orchestrator
chmod +x target/native-package-test/bin/api-test-orchestrator
mvn -B -DskipTests dependency:go-offline
scripts/package-native-release.sh target/native-package-test/bin/api-test-orchestrator v0.1.0 target/native-package-test
tar -tzf target/native-package-test/api-test-orchestrator-v0.1.0-linux-x64.tar.gz | sort
cat target/native-package-test/api-test-orchestrator-v0.1.0-linux-x64.tar.gz.sha256
```

Expected tar listing includes exactly these required paths among any directory entries:

```text
api-test-orchestrator-v0.1.0-linux-x64/LICENSE
api-test-orchestrator-v0.1.0-linux-x64/NOTICE
api-test-orchestrator-v0.1.0-linux-x64/THIRD-PARTY.txt
api-test-orchestrator-v0.1.0-linux-x64/api-test-orchestrator
api-test-orchestrator-v0.1.0-linux-x64/licenses/mysql-connector-j/INFO_BIN
api-test-orchestrator-v0.1.0-linux-x64/licenses/mysql-connector-j/INFO_SRC
api-test-orchestrator-v0.1.0-linux-x64/licenses/mysql-connector-j/LICENSE
api-test-orchestrator-v0.1.0-linux-x64/licenses/mysql-connector-j/README
```

Expected checksum output ends with:

```text
api-test-orchestrator-v0.1.0-linux-x64.tar.gz
```

- [ ] **Step 5: Commit**

Run:

```bash
git add scripts/package-native-release.sh
git commit -m "build(native): package native release archive"
```

---

### Task 3: Add Native Assets To Release Workflow

**Files:**
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: `scripts/package-native-release.sh` from Task 2 and native profile from Task 1.
- Produces: GitHub Release assets `api-test-orchestrator-v0.1.0-linux-x64.tar.gz` and `.sha256` for the current release tag.

- [ ] **Step 1: Add native asset output names**

In the `Derive release version` step, after the existing checksum output lines, add:

```bash
          echo "native_name=api-test-orchestrator-${tag}-linux-x64.tar.gz" >> "${GITHUB_OUTPUT}"
          echo "native_checksum_name=api-test-orchestrator-${tag}-linux-x64.tar.gz.sha256" >> "${GITHUB_OUTPUT}"
```

- [ ] **Step 2: Add GraalVM setup and native build steps**

Insert these steps after `Prepare release assets` and before `Create or update GitHub Release`:

```yaml
      - name: Set up GraalVM
        uses: graalvm/setup-graalvm@v1
        with:
          distribution: graalvm
          java-version: "21"
          components: native-image
          github-token: ${{ github.token }}

      - name: Build native binary
        run: mvn -B -Pnative -DskipTests package

      - name: Verify native binary
        shell: bash
        run: |
          set -euo pipefail

          native_path="target/api-test-orchestrator"
          test -x "${native_path}"
          test "$("${native_path}" --version)" = "${{ steps.release.outputs.version }}"
          "${native_path}" --help >/tmp/api-test-orchestrator-help.txt

      - name: Prepare native release assets
        shell: bash
        run: |
          set -euo pipefail

          ./scripts/package-native-release.sh \
            target/api-test-orchestrator \
            "${{ steps.release.outputs.tag }}" \
            "${PWD}"
```

- [ ] **Step 3: Upload native assets with the existing release assets**

Replace the variable block in `Create or update GitHub Release` with:

```bash
          tag="${{ steps.release.outputs.tag }}"
          jar_name="${{ steps.release.outputs.jar_name }}"
          checksum_name="${{ steps.release.outputs.checksum_name }}"
          native_name="${{ steps.release.outputs.native_name }}"
          native_checksum_name="${{ steps.release.outputs.native_checksum_name }}"
```

Replace the existing `gh release upload` line with:

```bash
            gh release upload "${tag}" \
              "${jar_name}" \
              "${checksum_name}" \
              "${native_name}" \
              "${native_checksum_name}" \
              --clobber
```

Replace the existing `gh release create` asset list with:

```bash
            gh release create "${tag}" \
              "${jar_name}" \
              "${checksum_name}" \
              "${native_name}" \
              "${native_checksum_name}" \
              --title "${tag}" \
              --generate-notes \
              --verify-tag
```

- [ ] **Step 4: Verify workflow syntax locally**

Run:

```bash
sed -n '1,180p' .github/workflows/release.yml
```

Expected: the release job includes `Set up GraalVM`, `Build native binary`, `Verify native binary`, and `Prepare native release assets` before `Create or update GitHub Release`.

- [ ] **Step 5: Commit**

Run:

```bash
git add .github/workflows/release.yml
git commit -m "ci(release): publish native linux asset"
```

---

### Task 4: Document Native Release Usage And Licenses

**Files:**
- Modify: `README.md`
- Modify: `THIRD-PARTY.txt`

**Interfaces:**
- Consumes: artifact names from Task 3.
- Produces: user-facing release and licensing documentation for the native Linux x64 archive.

- [ ] **Step 1: Update the README release asset list**

In `README.md`, replace:

```markdown
- `api-test-orchestrator-v0.1.0.jar`
- `api-test-orchestrator-v0.1.0.jar.sha256`
```

with:

```markdown
- `api-test-orchestrator-v0.1.0.jar`
- `api-test-orchestrator-v0.1.0.jar.sha256`
- `api-test-orchestrator-v0.1.0-linux-x64.tar.gz`
- `api-test-orchestrator-v0.1.0-linux-x64.tar.gz.sha256`
```

- [ ] **Step 2: Add native usage text after the release asset list**

Insert this text after the asset list:

````markdown
The Linux x64 native archive contains a single executable runtime binary plus
the project and third-party license notices:

```bash
tar -xzf api-test-orchestrator-v0.1.0-linux-x64.tar.gz
./api-test-orchestrator-v0.1.0-linux-x64/api-test-orchestrator --version
```

The native binary does not require a JVM and includes MySQL Connector/J for
MySQL-backed tests.
````

- [ ] **Step 3: Clarify shaded JAR versus native driver behavior**

In the `License and JDBC Drivers` section, replace:

```markdown
MySQL Connector/J is intentionally not bundled into the shaded JAR. Oracle
licenses Connector/J under GPLv2 with the Universal FOSS Exception, so keeping
it as a runtime-provided driver avoids presenting the executable JAR as if every
bundled component were Apache-2.0.
```

with:

```markdown
MySQL Connector/J is intentionally not bundled into the shaded JAR. Oracle
licenses Connector/J under GPLv2 with the Universal FOSS Exception, so keeping
it as a runtime-provided driver avoids presenting the executable JAR as if every
bundled component were Apache-2.0.

The Linux x64 native archive is different: it bundles MySQL Connector/J so the
native executable can connect to MySQL without a JVM classpath. That archive
includes Connector/J license and source metadata under
`licenses/mysql-connector-j/`.
```

- [ ] **Step 4: Update THIRD-PARTY native component notices**

In `THIRD-PARTY.txt`, replace the opening paragraph:

```text
This project is licensed under Apache License 2.0. The shaded executable JAR
also bundles third-party software that remains under its own license terms.
```

with:

```text
This project is licensed under Apache License 2.0. The shaded executable JAR
and Linux x64 native archive bundle third-party software that remains under its
own license terms.
```

After the `Bundled in the shaded JAR` dependency list and before `Runtime-provided database drivers`, insert:

```text
Bundled only in the Linux x64 native archive
--------------------------------------------

- com.mysql:mysql-connector-j:9.2.0 - GPLv2 with Universal FOSS Exception 1.0
- com.google.protobuf:protobuf-java:4.29.0 - BSD-3-Clause

The native archive includes MySQL Connector/J license and source metadata under
licenses/mysql-connector-j/.
```

- [ ] **Step 5: Verify docs mention all native assets**

Run:

```bash
rg -n "linux-x64|mysql-connector-j|Universal FOSS|native" README.md THIRD-PARTY.txt
```

Expected: output includes the native archive asset names, the native binary usage note, and Connector/J licensing text.

- [ ] **Step 6: Commit**

Run:

```bash
git add README.md THIRD-PARTY.txt
git commit -m "docs(native): document native release artifact"
```

---

### Task 5: Full Local Verification

**Files:**
- No source changes expected.

**Interfaces:**
- Consumes: all previous task outputs.
- Produces: verified local native build and package.

- [ ] **Step 1: Run the JVM test suite**

Run:

```bash
mvn -B test
```

Expected: `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 2: Verify the shaded JAR still excludes MySQL and protobuf**

Run:

```bash
mvn -B -DskipTests package
jar tf target/api-test-orchestrator-0.1.0-SNAPSHOT.jar > target/jar-contents.txt
if rg "^(com/mysql|com/google/protobuf)|mysql-connector|protobuf-java" target/jar-contents.txt; then
  echo "Unexpected MySQL/protobuf content in shaded JAR" >&2
  exit 1
fi
rg -n "^META-INF/(LICENSE|NOTICE|THIRD-PARTY\\.txt)$" target/jar-contents.txt
```

Expected: no MySQL/protobuf matches, and the three `META-INF` files are present.

- [ ] **Step 3: Verify the native executable**

Run with GraalVM JDK 21 active:

```bash
mvn -B -Pnative -DskipTests package
test -x target/api-test-orchestrator
target/api-test-orchestrator --version
target/api-test-orchestrator --help >/tmp/api-test-orchestrator-native-help.txt
```

Expected:

```text
0.1.0
```

- [ ] **Step 4: Verify the native archive package**

Run:

```bash
scripts/package-native-release.sh target/api-test-orchestrator v0.1.0 target
tar -tzf target/api-test-orchestrator-v0.1.0-linux-x64.tar.gz | sort > target/native-archive-contents.txt
rg -n "api-test-orchestrator-v0.1.0-linux-x64/(api-test-orchestrator|LICENSE|NOTICE|THIRD-PARTY\\.txt|licenses/mysql-connector-j/(LICENSE|README|INFO_BIN|INFO_SRC))$" target/native-archive-contents.txt
cat target/api-test-orchestrator-v0.1.0-linux-x64.tar.gz.sha256
```

Expected: all required package paths are listed and the checksum references `api-test-orchestrator-v0.1.0-linux-x64.tar.gz`.

- [ ] **Step 5: Resolve any verification-only fixes through the owning task**

If verification required a source change, return to the task that owns the file,
make the smallest correction there, rerun that task's verification command, and
amend that task's commit with the same commit message. For example, if the fix
is in `pom.xml`, rerun Task 1 Step 5 and then run:

```bash
git add pom.xml
git commit --amend --no-edit
```

If verification required no source changes, do not create a commit.

---

### Task 6: Release Re-tag And Remote Verification

**Files:**
- No source changes expected.

**Interfaces:**
- Consumes: verified local branch.
- Produces: remote `main` and remote `v0.1.0` tag both resolving to the final implementation commit.

- [ ] **Step 1: Confirm final local state**

Run:

```bash
git status --short --branch
git log --oneline --decorate -5
```

Expected: only intentionally untracked local release artifacts may remain; source changes are committed.

- [ ] **Step 2: Move local release tag to final HEAD**

Run:

```bash
git tag -f -a v0.1.0 -m "Release v0.1.0" HEAD
```

Expected: Git reports `Updated tag 'v0.1.0'`.

- [ ] **Step 3: Push main and force-update the release tag**

Run:

```bash
git push origin main
git push --force origin v0.1.0
```

Expected: remote `main` updates and remote `v0.1.0` receives a forced update.

- [ ] **Step 4: Watch the release workflow**

Run:

```bash
gh run list --workflow Release --limit 3
run_id="$(gh run list --workflow Release --limit 1 --json databaseId --jq '.[0].databaseId')"
gh run watch "${run_id}" --exit-status
```

Expected: the new `Release` workflow run completes successfully.

- [ ] **Step 5: Verify remote tag and release assets**

Run:

```bash
git ls-remote origin refs/heads/main refs/tags/v0.1.0 'refs/tags/v0.1.0^{}'
gh release view v0.1.0 --json tagName,targetCommitish,url,assets
```

Expected:

- `refs/heads/main` and `refs/tags/v0.1.0^{}` resolve to the same final commit.
- Release assets include:
  - `api-test-orchestrator-v0.1.0.jar`
  - `api-test-orchestrator-v0.1.0.jar.sha256`
  - `api-test-orchestrator-v0.1.0-linux-x64.tar.gz`
  - `api-test-orchestrator-v0.1.0-linux-x64.tar.gz.sha256`
