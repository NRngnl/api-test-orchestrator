package io.vtz.apitest.infrastructure.process;

import io.vtz.apitest.application.port.CommandRunnerPort;
import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.process.CommandResult;
import io.vtz.apitest.domain.process.ExecCommandSpec;
import io.vtz.apitest.infrastructure.log.JsonLogParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
    private final JsonLogParser logParser = new JsonLogParser();

    @Override
    public CommandResult run(ExecCommandSpec spec, Consumer<LogEvent> onLogLine) {
        try {
            ProcessBuilder builder = new ProcessBuilder(spec.command());
            if (spec.workingDir() != null) {
                builder.directory(spec.workingDir().toFile());
            }
            Map<String, String> environment = builder.environment();
            spec.envDefaults().forEach(environment::putIfAbsent);
            environment.putAll(spec.envOverrides());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder captured = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (onLogLine != null) {
                        onLogLine.accept(logParser.parse(line));
                    }
                    captured.append(line).append(System.lineSeparator());
                }
            }
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, captured.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running command: " + spec.command(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run command: " + spec.command(), e);
        }
    }
}
