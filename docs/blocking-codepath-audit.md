# Blocking Codepath Audit

Date: 2026-07-08

Scope: current uncommitted worktree on `fix/review`, with emphasis on code paths that can block, deadlock, hang, or race under slow sinks, stuck child processes, interrupted waits, or unsafe release-script inputs.

## Subagents Used

- Sagan audited `ProcessCommandRunner`, `ProcessFacade`, `ApiLogFormatter`, and related tests.
- Fermat audited CLI API process lifecycle, process log draining, correlator state, and mock lifecycle.
- Heisenberg audited scripts, release packaging, docs instructions, and blocking remote commands.

The findings below were verified in the main workspace before inclusion. Subagent-only claims that could not be verified are marked separately.

## Verification Commands

```bash
git diff --check
mise exec -- mvn -q verify
bash -n scripts/package-native-release.sh
bash -n scripts/sync-runner-deps.sh
bash -n docker/runner/entrypoint.sh
```

Additional bounded probes were run through `timeout` and `jshell` against `target/classes` plus the Maven dependency classpath. These probes intentionally exercised hanging paths without allowing the shell session to hang indefinitely.

## 2026-07-09 Remediation Update

The report claims were re-verified against the current worktree, then fixed where they were not false positives.

Fixed:

- `execCmd` now closes child stdin, accepts `timeoutSeconds`, defaults to a bounded timeout, and kills timed-out processes.
- API process stdout/stderr are drained before display/log sinks through bounded async sinks.
- `awaitReady` now exits promptly when the process exits or the waiting thread is interrupted.
- API process forced shutdown now waits after `destroyForcibly()` before reading the exit value.
- `RequestLogCorrelator.findByFailureUrl()` now returns immutable snapshots.
- `package-native-release.sh` now rejects archive-unsafe tags/platforms, keeps staging under the output directory, and copies project notice files via `repo_root`.
- The release verification plan now bounds `gh run watch` with `timeout 60m`.
- Stale exact test-count expectations were replaced with "all tests pass" expectations.
- The native release design now clarifies that native workflow publishing is planned behavior until the workflow task is applied.

Regression coverage added:

- `ProcessCommandRunnerTest` covers stdin EOF behavior and command timeout.
- `ApiProcessSupervisorTest` covers slow stdout sinks, early readiness failure after process exit, and interrupt handling.
- `RequestLogCorrelatorTest` covers immutable snapshots.
- `ProcessFacadeTest` covers timeout option parsing and invalid timeout rejection.
- `ProcessSpecTest` covers sub-millisecond timeout/health interval rejection.

Verification after remediation:

```bash
mise exec -- mvn -q -Dtest=ProcessCommandRunnerTest,ApiProcessSupervisorTest,RequestLogCorrelatorTest,ProcessFacadeTest,ProcessSpecTest test
mise exec -- mvn -q test
mise exec -- mvn -q verify
bash -n scripts/package-native-release.sh
bash -n scripts/sync-runner-deps.sh
bash -n docker/runner/entrypoint.sh
```

Package-script smoke probes also verified that packaging succeeds from outside
the repository root, rejects `v/../../escape`, and still produces the expected
archive layout for a normal tag.

## Original Confirmed Blocking Or Race Risks Before Remediation

### 1. `execCmd` Can Block Forever When The Child Waits On Stdin

Original status: confirmed by runtime probe.

Remediation status: fixed on 2026-07-09 by closing the child process stdin stream immediately after start and adding a regression test.

Evidence:

- `ProcessCommandRunner` originally started a child process without redirecting stdin or closing `process.getOutputStream()`: `src/main/java/io/vtz/apitest/infrastructure/process/ProcessCommandRunner.java`.
- It then read process output until EOF in the same runner path.
- `ProcessFacade.execCmd` exposes arbitrary command args from Karate/JavaScript: `src/main/java/io/vtz/apitest/interfaces/facade/ProcessFacade.java`.

Probe:

```text
r.run(new ExecCommandSpec(List.of("sh", "-c", "echo child-start; read ignored"), ...))
```

Result: the outer `timeout 5s` killed the probe with exit code `124`. `probe:done` was never printed.

Impact: a feature that calls `ato.helpers().execCmd(...)` can hang indefinitely if the command reads stdin.

Verification: `ProcessCommandRunnerTest.stdinReadingCommandDoesNotBlockForever`.

### 2. `execCmd` Has No Timeout For Long-Running Or Stuck Commands

Original status: confirmed by source inspection; stdin probe demonstrated one concrete hang case.

Remediation status: fixed on 2026-07-09 by adding `timeoutSeconds` / `ExecCommandSpec.timeout`, enforcing bounded `waitFor(...)`, and killing/reaping timed-out processes.

Evidence:

- Original evidence: `ExecCommandSpec` had no timeout field, `ProcessCommandRunner` used unbounded process waiting, and `ProcessFacade.execCmd` had no timeout option.

Impact: commands such as `tail -f`, a server process, or a network command stuck below the OS timeout can block the scenario indefinitely.

Verification: `ProcessCommandRunnerTest.commandTimeoutKillsNonTerminatingProcess` and `ProcessFacadeTest.passesConfiguredTimeoutToRunner`.

### 3. API Process Log Draining Can Block The Child API

Original status: confirmed by runtime probe.

Remediation status: fixed on 2026-07-09 by routing API stdout/stderr events through bounded async sinks before display/log callbacks.

Evidence:

- `ApiProcessSupervisor.readStdout` and `readStderr` originally called their sinks synchronously inside pipe-drain loops: `src/main/java/io/vtz/apitest/infrastructure/process/ApiProcessSupervisor.java`.
- The CLI sink correlates, formats, and prints logs in that path: `src/main/java/io/vtz/apitest/interfaces/cli/ApiTestOrchestratorCli.java`.

Probe:

```text
ApiProcessSupervisor started `yes line | head -n 200000`
stdout sink slept 10s on each event
after 2s: probe:running=true
stop: probe:stop=143
```

Impact: a chatty API process can block on a full stdout/stderr pipe if log formatting, correlation, or terminal output blocks.

Verification: `ApiProcessSupervisorTest.slowStdoutSinkDoesNotBlockApiOutputDrain`.

### 4. `awaitReady` Waits The Full Timeout After The API Process Already Exited

Original status: confirmed by runtime probe.

Remediation status: fixed on 2026-07-09 by checking process liveness in `awaitReady`.

Evidence:

- `awaitReady` originally looped only on deadline and socket connectivity: `src/main/java/io/vtz/apitest/infrastructure/process/ApiProcessSupervisor.java`.
- It does not check whether `process` is already dead.

Probe:

```text
command: sh -c 'exit 7'
healthTimeout: 2s
result: probe:ready=false elapsedMs=2123 running=false
```

Impact: failed API startup waits for the configured health timeout even when the process has already exited.

Verification: `ApiProcessSupervisorTest.awaitReadyReturnsPromptlyWhenProcessExits`.

### 5. `awaitReady` Ignores Interrupts Until The Deadline

Original status: source-confirmed; not separately runtime-probed.

Remediation status: fixed on 2026-07-09 by making interrupted sleeps stop readiness waiting.

Evidence:

- `sleep` originally restored interrupt status but returned to the caller: `src/main/java/io/vtz/apitest/infrastructure/process/ApiProcessSupervisor.java`.
- `awaitReady` did not check `Thread.currentThread().isInterrupted()` before continuing the loop.

Impact: an interrupted caller can keep waiting until `healthTimeout`.

Verification: `ApiProcessSupervisorTest.awaitReadyReturnsPromptlyWhenInterrupted`.

### 6. Forced API Shutdown Has A Reap Race

Original status: source-confirmed; not deterministically reproduced.

Remediation status: fixed on 2026-07-09 by waiting after `destroyForcibly()` before reading `exitValue()`.

Evidence:

- `stop()` originally called `process.destroyForcibly()` when graceful shutdown exceeded 5 seconds: `src/main/java/io/vtz/apitest/infrastructure/process/ApiProcessSupervisor.java`.
- It then immediately called `process.exitValue()` without waiting for forced termination to complete.

Impact: `exitValue()` may throw `IllegalThreadStateException` during close if the forced kill has not completed yet.

Verification: covered by `mise exec -- mvn -q verify`; the race was not deterministic enough for a focused reproducer.

### 7. `RequestLogCorrelator.findByFailureUrl()` Returns Live Mutable State

Original status: confirmed by runtime probe.

Remediation status: fixed on 2026-07-09 by returning `List.copyOf(...)` snapshots.

Evidence:

- `accept()` mutates per-request `ArrayList` instances under synchronization: `src/main/java/io/vtz/apitest/infrastructure/log/RequestLogCorrelator.java`.
- `findByFailureUrl()` originally returned the stored list directly.
- `lastRequestLogs()` already used `List.copyOf(...)`, showing the safer pattern existed.

Probe:

```text
probe:size-before=1
probe:size-after=2
probe:stored-after-clear=0
```

Impact: callers can mutate internal state, or iterate while API log threads append, causing inconsistent results or `ConcurrentModificationException`.

Verification: `RequestLogCorrelatorTest.returnsImmutableSnapshotForFailureUrlLogs`.

### 8. `package-native-release.sh` Is Cwd-Sensitive

Original status: confirmed by runtime probe.

Remediation status: fixed on 2026-07-09 by copying notice files from `repo_root`.

Evidence:

- The script computes `repo_root`: `scripts/package-native-release.sh`.
- It originally copied `LICENSE NOTICE THIRD-PARTY.txt` via cwd-relative paths.

Probe:

```text
cd /private/tmp
/Users/anthony/DevProjects/api-test-orchestrator/scripts/package-native-release.sh ...
cp: cannot stat 'LICENSE': No such file or directory
cp: cannot stat 'NOTICE': No such file or directory
cp: cannot stat 'THIRD-PARTY.txt': No such file or directory
```

Impact: the script fails or copies the wrong files when invoked outside repo root.

Verification: package smoke from `/private/tmp` succeeds.

### 9. `package-native-release.sh` Allows Slash Tags To Escape The Output Directory Shape

Original status: confirmed by runtime probe.

Remediation status: fixed on 2026-07-09 by validating tag/platform archive names and resolving staging under the output directory.

Evidence:

- The script originally accepted `tag` from argv and only checked that it started with `v`: `scripts/package-native-release.sh`.
- It interpolates `tag` into `archive_base`, then into `staging_dir`, and removes that path.

Probe:

```text
scripts/package-native-release.sh <binary> 'v/../../escape' /private/tmp/ato-tag-probe/out
status=0
/private/tmp/ato-tag-probe/escape-linux-x64
/private/tmp/ato-tag-probe/out/api-test-orchestrator-v
```

Impact: slash/path-traversal tags can create or remove staging directories outside the requested output directory shape.

Verification: package smoke rejects `v/../../escape` and creates no escaped staging directory.

### 10. Release Verification Doc Uses Unbounded `gh run watch`

Original status: source-confirmed.

Remediation status: fixed on 2026-07-09 by wrapping the watch command in `timeout 60m`.

Evidence:

- The plan originally used `gh run watch "${run_id}" --exit-status` with no timeout or polling bound: `docs/superpowers/plans/2026-07-08-native-image-release.md`.

Impact: an interactive release verification can block indefinitely if the run stalls or the CLI loses progress.

Verification: `docs/superpowers/plans/2026-07-08-native-image-release.md` now uses `timeout 60m gh run watch ...`.

## Documentation Or Workflow Drift Found During Audit

### Native Release Docs Are Ahead Of The Actual Workflow

Original status: confirmed by source inspection.

Remediation status: fixed on 2026-07-09 by clarifying that native workflow publishing is planned behavior until the workflow task is applied.

Evidence:

- The design says the release workflow adds native build/package/upload steps: `docs/superpowers/specs/2026-07-08-native-image-release-design.md`.
- The current workflow uploads only `release-jar` in the jar job: `.github/workflows/release.yml`.
- The publish job downloads `release-*` artifacts and publishes `dist/*`.

Impact: a tag pushed now will not publish the native tarball described by the docs.

### Test Count Expectations Are Stale

Original status: confirmed by grep.

Remediation status: fixed on 2026-07-09 by removing exact test-count expectations from the plan.

Evidence:

- Original evidence: the plan expected an exact test count while the current worktree had 67 `@Test` occurrences.

Impact: exact test-count instructions are misleading and will continue drifting.

Verification: the plan now expects all tests to pass without naming an exact count.

## False Positives Or Mitigated Paths

### `ProcessCommandRunner` Slow Log Consumers No Longer Block Pipe Draining

Status: false positive for the current batch command path.

Evidence:

- The runner drains process output on the runner thread and enqueues log events with non-blocking `queue.offer(...)`: `src/main/java/io/vtz/apitest/infrastructure/process/ProcessCommandRunner.java` and `src/main/java/io/vtz/apitest/infrastructure/process/AsyncSink.java`.
- A bounded queue avoids unbounded memory growth: `src/main/java/io/vtz/apitest/infrastructure/process/AsyncSink.java`.
- The slow-consumer regression test is present: `src/test/java/io/vtz/apitest/infrastructure/process/ProcessCommandRunnerTest.java`.
- `mise exec -- mvn -q verify` passed.

Residual risk: live streamed log events can be dropped under backpressure, but `CommandResult.output()` still captures every process output line.

### `ApiLogFormatter` Is Not Itself A Deadlock Source In The Batch Path

Status: false positive for the current batch `execCmd` and API-process paths.

Evidence:

- `ApiLogFormatter.line(...)` is synchronous, but in the batch command path it runs on the `AsyncSink` worker, not in the pipe-drain loop.
- In the API process path, formatting and `System.out.println(...)` also run on an `AsyncSink` worker, not the supervisor stdout reader.

### `KarateMockServerRegistry` Does Not Self-Deadlock On `stopAll()`

Status: false positive.

Evidence:

- Registry methods are `synchronized`, but Java monitors are reentrant.
- `stopAll()` snapshots IDs with `toList()` before calling `stop(...)`: `src/main/java/io/vtz/apitest/infrastructure/karate/KarateMockServerRegistry.java`.

Residual risk: `ApiTestOrchestrator` intentionally uses one static process-global registry, so one orchestrator's `close()` calls `stopAll()` for shared mocks: `src/main/java/io/vtz/apitest/interfaces/facade/ApiTestOrchestrator.java`. That is a cross-instance lifecycle risk, not a self-deadlock.

### Connector/J Version Drift In Current User Docs Was Not Found

Status: false positive after current worktree edits.

Evidence:

- Stale `9.2.0` and `4.29.0` references were not found in current README, notices, scripts, and superpowers docs.
- `scripts/package-native-release.sh` now derives Connector/J from `mysql.connector.version`.

## Recommended Fix Order

1. Add stdin closure and timeout enforcement to `execCmd`/`ProcessCommandRunner`.
2. Apply bounded async draining to `ApiProcessSupervisor` stdout/stderr sinks.
3. Make `awaitReady` process-exit and interrupt aware; fix forced-kill reaping.
4. Return immutable snapshots from `RequestLogCorrelator.findByFailureUrl()`.
5. Harden `package-native-release.sh` cwd handling and tag/platform validation.
6. Update release docs/workflow drift and remove exact test-count expectations.
