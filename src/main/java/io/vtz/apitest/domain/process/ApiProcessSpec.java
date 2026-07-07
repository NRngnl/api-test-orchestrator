package io.vtz.apitest.domain.process;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record ApiProcessSpec(
        List<String> command,
        Path workingDir,
        URI healthUrl,
        Duration healthTimeout,
        Duration healthInterval,
        Map<String, String> environment
) {
    public ApiProcessSpec {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        command = List.copyOf(command);
        healthTimeout = healthTimeout == null ? Duration.ofSeconds(30) : healthTimeout;
        healthInterval = healthInterval == null ? Duration.ofSeconds(1) : healthInterval;
        environment = Map.copyOf(environment == null ? Map.of() : environment);
    }
}
