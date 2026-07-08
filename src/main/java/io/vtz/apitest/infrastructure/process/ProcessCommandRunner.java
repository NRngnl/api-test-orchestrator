package io.vtz.apitest.infrastructure.process;

import io.vtz.apitest.application.port.CommandRunnerPort;
import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.process.CommandResult;
import io.vtz.apitest.domain.process.ExecCommandSpec;
import io.vtz.apitest.infrastructure.log.JsonLogParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Runs a command to completion via {@link ProcessBuilder}, inheriting the
 * parent environment and streaming its combined stdout/stderr line-by-line as
 * parsed {@link LogEvent}s (so callers can render them exactly like the API
 * process logs). Env layering mirrors godotenv's no-overwrite {@code Load}:
 * file defaults are applied only when the parent environment lacks the key,
 * while explicit overrides always win.
 */
public class ProcessCommandRunner implements CommandRunnerPort {
    private static final Duration OUTPUT_DRAIN_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration FORCED_STOP_TIMEOUT = Duration.ofSeconds(5);

    private final JsonLogParser logParser = new JsonLogParser();

    @Override
    public CommandResult run(ExecCommandSpec spec, Consumer<LogEvent> onLogLine) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(spec.command());
            if (spec.workingDir() != null) {
                builder.directory(spec.workingDir().toFile());
            }
            Map<String, String> environment = builder.environment();
            spec.envDefaults().forEach(environment::putIfAbsent);
            environment.putAll(spec.envOverrides());
            builder.redirectErrorStream(true);
            process = builder.start();
            process.getOutputStream().close();
            StringBuilder captured = new StringBuilder();
            AtomicReference<RuntimeException> outputFailure = new AtomicReference<>();
            try (var logSink = new AsyncSink<>("ato-command-log-consumer", onLogLine)) {
                Process runningProcess = process;
                Thread outputReader = Thread.ofVirtual()
                        .name("ato-command-output-reader")
                        .start(() -> readOutput(runningProcess, logSink, captured, outputFailure));
                if (!process.waitFor(spec.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    IllegalStateException timeout = new IllegalStateException(
                            "Command timed out after " + spec.timeout() + ": " + spec.command());
                    destroy(process);
                    try {
                        joinOutputReader(outputReader);
                    } catch (IllegalStateException e) {
                        timeout.addSuppressed(e);
                    }
                    throw timeout;
                }
                joinOutputReader(outputReader);
            }
            RuntimeException failure = outputFailure.get();
            if (failure != null) {
                throw failure;
            }
            int exitCode = process.exitValue();
            return new CommandResult(exitCode, captured.toString());
        } catch (InterruptedException e) {
            destroy(process);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running command: " + spec.command(), e);
        } catch (IllegalStateException e) {
            destroy(process);
            throw e;
        } catch (Exception e) {
            destroy(process);
            throw new IllegalStateException("Failed to run command: " + spec.command(), e);
        }
    }

    private void readOutput(
            Process process,
            AsyncSink<LogEvent> logSink,
            StringBuilder captured,
            AtomicReference<RuntimeException> failure) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logSink.accept(logParser.parse(line));
                captured.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            failure.compareAndSet(null, new IllegalStateException("Failed to read command output", e));
        }
    }

    private static void joinOutputReader(Thread outputReader) throws InterruptedException {
        outputReader.join(OUTPUT_DRAIN_TIMEOUT.toMillis());
        if (outputReader.isAlive()) {
            outputReader.interrupt();
            throw new IllegalStateException("Timed out while draining command output");
        }
    }

    private static void destroy(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroyForcibly();
        try {
            process.waitFor(FORCED_STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
