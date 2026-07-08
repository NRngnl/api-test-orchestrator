package io.vtz.apitest.domain.process;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record ExecCommandSpec(
        List<String> command,
        Path workingDir,
        Map<String, String> envDefaults,
        Map<String, String> envOverrides,
        Duration timeout
) {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration MIN_TIMEOUT = Duration.ofMillis(1);

    public ExecCommandSpec(
            List<String> command,
            Path workingDir,
            Map<String, String> envDefaults,
            Map<String, String> envOverrides) {
        this(command, workingDir, envDefaults, envOverrides, DEFAULT_TIMEOUT);
    }

    public ExecCommandSpec {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        command = List.copyOf(command);
        envDefaults = Map.copyOf(envDefaults == null ? Map.of() : envDefaults);
        envOverrides = Map.copyOf(envOverrides == null ? Map.of() : envOverrides);
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.compareTo(MIN_TIMEOUT) < 0) {
            throw new IllegalArgumentException("timeout must be at least 1 millisecond");
        }
    }
}
