# Native Image Release Design

## Summary

Add a GraalVM Native Image release path for API Test Orchestrator while keeping
the current shaded JAR release unchanged. The first native target is Linux x64.
The native release includes MySQL Connector/J so users can run MySQL-backed
tests without supplying an external driver JAR at runtime.

The existing shaded JAR remains driver-neutral and continues to exclude MySQL
Connector/J and protobuf. This preserves the current flexible JVM classpath
workflow for users who want to supply their own JDBC drivers.

## Licensing Assumption

This project remains Apache-2.0. MySQL Connector/J 9.2.0 declares GPLv2 with the
Universal FOSS Exception v1.0. Oracle's Universal FOSS Exception permits
distribution and linking with OSI-approved or FSF-free software while keeping
each portion under its own license. Apache-2.0 is OSI-approved, so the native
release can include Connector/J if the release documentation preserves notices
and identifies Connector/J as a separately licensed bundled component.

This is an implementation assumption, not legal advice. The release must make
the license boundary clear:

- Project source: Apache-2.0.
- Native binary bundled component: MySQL Connector/J under GPLv2 with Universal
  FOSS Exception v1.0.
- Native binary bundled component: protobuf-java under its upstream license as a
  Connector/J dependency.

## Release Artifacts

Tag-driven releases continue to publish:

- `api-test-orchestrator-v<version>.jar`
- `api-test-orchestrator-v<version>.jar.sha256`

The native release adds Linux x64 assets:

- `api-test-orchestrator-v<version>-linux-x64.tar.gz`
- `api-test-orchestrator-v<version>-linux-x64.tar.gz.sha256`

The tarball contains:

- `api-test-orchestrator`
- `LICENSE`
- `NOTICE`
- `THIRD-PARTY.txt`
- Connector/J license and source/license metadata copied from the dependency
  artifact, stored under `licenses/mysql-connector-j/`

A tarball is preferred over a bare binary because it keeps required license and
notice files attached to the native distribution. The executable inside the
tarball remains a single runtime binary.

## Maven Build Design

Add a `native` Maven profile using `org.graalvm.buildtools:native-maven-plugin`.
The profile sets:

- Main class: `io.vtz.apitest.interfaces.cli.ApiTestOrchestratorCli`
- Image name: `api-test-orchestrator`
- Fallback disabled
- Java release 21, matching the existing project build

The native profile uses the project dependency graph directly rather than the
shaded JAR. That lets the existing shaded JAR exclusions remain unchanged while
the native build includes direct dependencies that are reachable at build time,
including MySQL Connector/J and protobuf.

If GraalVM reports missing reflection, resource, proxy, or serialization
registrations, add targeted metadata under:

`src/main/resources/META-INF/native-image/io.vtz/api-test-orchestrator/reachability-metadata.json`

Metadata should be minimal and tied to observed failures. The first pass should
avoid broad "include everything" reflection configuration.

## GitHub Actions Design

The release workflow keeps the current JAR job steps:

1. Derive release version from `v*` tag.
2. Set Maven release version.
3. Run `mvn -B test`.
4. Build shaded JAR.
5. Verify MySQL Connector/J and protobuf are not bundled in the shaded JAR.
6. Prepare JAR and checksum assets.

Then it adds native steps on Ubuntu:

1. Set up GraalVM for Java 21 with Native Image support.
2. Build the native binary with `mvn -B -Pnative -DskipTests package`.
3. Run smoke checks:
   - `target/api-test-orchestrator --version` prints the release version.
   - `target/api-test-orchestrator --help` exits successfully.
4. Package the native binary with license and notice files into the Linux x64
   tarball.
5. Generate a SHA-256 checksum.
6. Upload all release assets with the existing create-or-update logic.

## Runtime Behavior

The native binary should work without a JVM. For MySQL-backed tests, users run
the native executable directly:

```bash
./api-test-orchestrator --config examples/api-test-orchestrator.yml /tests/create_case.feature
```

No `mysql-connector-j.jar` runtime classpath argument is required for the native
Linux x64 release. The JVM JAR release continues to require external JDBC
drivers for MySQL-backed tests.

## Error Handling

Native-image build failures are handled as CI failures. The expected failure
classes are:

- Unsupported dynamic reflection/resource access.
- Class initialization issues.
- Native-image incompatibilities in Karate, HikariCP, Connector/J, protobuf, or
  Logback.

The implementation should fix these by adding targeted build arguments or
reachability metadata, then rerunning both the native build and the JVM test
suite. If a dependency cannot be made native-compatible in a focused way, the
native release should be left disabled rather than shipping a partially working
binary.

## Testing And Verification

Required local checks:

- `mvn -B test`
- `mvn -B -Pnative -DskipTests package`
- `target/api-test-orchestrator --version`

Required release workflow checks:

- JVM tests pass.
- Shaded JAR still excludes MySQL Connector/J and protobuf.
- Native binary builds on Ubuntu.
- Native binary reports the release version.
- Native tarball includes project notices and Connector/J license metadata.

Optional later check:

- Add a containerized MySQL smoke test that verifies a native binary can open a
  JDBC connection using Connector/J.

## Out Of Scope

- macOS and Windows native binaries.
- Loading arbitrary external JDBC drivers from a native binary at runtime.
- Replacing the shaded JAR release.
- Changing project versioning beyond the existing tag-derived release version.
