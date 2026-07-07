# API Test Orchestrator

Java-based orchestration framework for API scenario test suites.

This project is intentionally generic. It currently uses Karate v2 as an
embedded engine, but the project name, Java package, CLI, and facade are not
named after that engine.

It replaces process-wrapper style monitoring with a library-first design:

- Karate is executed through `io.karatelabs.core.Runner`.
- Mock servers are started through `io.karatelabs.core.MockServer`.
- Database fixture access and row preparation live behind generic Java services
  instead of large `karate-config.js` scripts.
- API process logs are parsed, filtered, correlated, and summarized by Java.
- Feature files call a small facade exposed by a thin `karate-config.js`.

## Thin `karate-config.js`

```javascript
function fn() {
  const Framework = Java.type("io.vtz.apitest.interfaces.facade.ApiTestOrchestrator");
  return { ato: Framework.fromEnvironment() };
}
```

Then features can call Java-backed helpers:

```gherkin
* def inserted = ato.db().insertSafe('cases', { business_date: ato.date().sqlDate(2025, 10, 1) })
* def auditRows = ato.db('audit').query('select * from audit_events')
* def fixtureKey = ato.ids().randomString()
* def sqs = ato.mocks().start({ feature: 'classpath:aws/sqs-success.feature', port: 39992, ssl: true })
* match ato.checksum().md5('{"batchID":2}') == 'c59b879de192eae4320a260a504ce0be'
* assert ato.helpers().isBefore('2025-10-01', '2025-10-31')
* ato.mocks().stopAll()
```

A compatibility bridge is also available when an existing suite already has
global helper names:

```javascript
function fn() {
  const Framework = Java.type("io.vtz.apitest.interfaces.facade.ApiTestOrchestrator");
  const ato = Framework.fromEnvironment();
  const helpers = ato.helpers();
  return {
    ato,
    dbQuery: (sql) => helpers.dbQuery(sql),
    auditQuery: (sql) => helpers.dbQuery("audit", sql),
    insertSafe: (table, row, ignore = []) => helpers.insertSafe(table, row, ignore),
    sqlDate: (y, m, d) => helpers.sqlDate(y, m, d),
    startMockServer: (config) => helpers.startMockServer(config)
  };
}
```

## Project-Specific Rules

Keep private backend rules in the consuming project, not in this public core.
For table-specific defaults, either provide config:

```yaml
database:
  defaults:
    tenant_id: 7
  tableDefaults:
    cases:
      status: OPEN
  generatedColumns:
    fixture_key:
      tables: [cases]
      length: 16
```

Multiple named databases are supported. `defaultName` is used by `ato.db()`,
`ato.helpers().dbQuery(sql)`, and other default helper calls. Additional targets
are accessed with `ato.db("name")` or named bridge methods.

```yaml
database:
  defaultName: primary
  targets:
    primary:
      jdbcUrl: jdbc:mysql://mysql:3306/app_db
      username: app
      password: secret
      defaults:
        tenant_id: 7
    audit:
      jdbcUrl: jdbc:mysql://mysql:3306/audit_db
      username: audit
      password: secret
      tableDefaults:
        events:
          source: feature
```

or publish a private wrapper/extension in that backend repo that returns the
same generic config object. This keeps domain names, business defaults, and
fixture conventions out of the reusable orchestrator.

## CLI

```bash
mvn -q -DskipTests package
java -jar target/api-test-orchestrator-0.1.0-SNAPSHOT.jar \
  --config examples/api-test-orchestrator.yml \
  /tests/create_case.feature
```

## Releases

GitHub Actions builds release JARs from version tags. To publish a release,
create and push a tag whose name starts with `v`:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The release workflow derives Maven version `0.1.0` from tag `v0.1.0`, runs the
test suite, builds the shaded JAR, verifies that MySQL Connector/J and
`protobuf-java` are not bundled, and publishes these GitHub Release assets:

- `api-test-orchestrator-v0.1.0.jar`
- `api-test-orchestrator-v0.1.0.jar.sha256`

If release creation fails with a permissions error, enable read/write workflow
permissions for GitHub Actions in the repository settings.

## License and JDBC Drivers

Copyright 2026 API Test Orchestrator contributors.

The project source is licensed under Apache License 2.0. The shaded executable
JAR bundles third-party libraries under their own licenses; see `NOTICE` and
`THIRD-PARTY.txt` for the bundled dependency notices.

MySQL Connector/J is intentionally not bundled into the shaded JAR. Oracle
licenses Connector/J under GPLv2 with the Universal FOSS Exception, so keeping
it as a runtime-provided driver avoids presenting the executable JAR as if every
bundled component were Apache-2.0.

For MySQL-backed tests, download or resolve the driver in the consuming project
and put it on the runtime classpath:

```bash
mvn dependency:copy \
  -Dartifact=com.mysql:mysql-connector-j:9.2.0 \
  -DoutputDirectory=target/drivers

java -cp "target/api-test-orchestrator-0.1.0-SNAPSHOT.jar:target/drivers/mysql-connector-j-9.2.0.jar" \
  io.vtz.apitest.interfaces.cli.ApiTestOrchestratorCli \
  --config examples/api-test-orchestrator.yml \
  /tests/create_case.feature
```

When distributing a package that includes extra JDBC drivers, include those
drivers' license files and notices alongside your distribution. If you do not
use MySQL, no MySQL driver is needed.

## Architecture

```text
src/main/java/io/vtz/apitest/
├── domain/          # Test, mock, DB, process, and log concepts
├── application/     # Use cases and ports
├── infrastructure/  # Karate, JDBC, process, config, and logging adapters
└── interfaces/      # CLI plus facade exposed to Karate feature files
```

The core rule is simple: Karate feature files describe scenarios; Java owns
orchestration, resources, database access, checksums, log filtering, and v2
runtime details.
