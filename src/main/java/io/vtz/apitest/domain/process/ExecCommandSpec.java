package io.vtz.apitest.domain.process;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ExecCommandSpec(
        List<String> command,
        Path workingDir,
        Map<String, String> envDefaults,
        Map<String, String> envOverrides
) {
    public ExecCommandSpec {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        command = List.copyOf(command);
        envDefaults = Map.copyOf(envDefaults == null ? Map.of() : envDefaults);
        envOverrides = Map.copyOf(envOverrides == null ? Map.of() : envOverrides);
    }
}
