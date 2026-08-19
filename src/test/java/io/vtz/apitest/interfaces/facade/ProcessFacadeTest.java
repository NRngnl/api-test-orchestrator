package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.port.CommandRunnerPort;
import io.vtz.apitest.application.service.DotenvParser;
import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.process.CommandResult;
import io.vtz.apitest.domain.process.ExecCommandSpec;
import io.vtz.apitest.infrastructure.config.FrameworkConfig;
import io.vtz.apitest.interfaces.cli.ApiLogFormatter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessFacadeTest {
    @Test
    void rejectsMissingOptionsBeforeStartingProcess() {
        RecordingCommandRunnerPort runner = new RecordingCommandRunnerPort();
        ProcessFacade facade = processFacade(runner);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> facade.execCmd(null));

        assertEquals("execCmd requires options", error.getMessage());
        assertNull(runner.lastSpec);
    }

    @Test
    void rejectsMissingArgsBeforeStartingProcess() {
        RecordingCommandRunnerPort runner = new RecordingCommandRunnerPort();
        ProcessFacade facade = processFacade(runner);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.execCmd(Map.of("env", Map.of("FOO", "bar"))));

        assertEquals("execCmd requires 'args' as a non-empty list", error.getMessage());
        assertNull(runner.lastSpec);
    }

    @Test
    void rejectsBlankCommandBeforeStartingProcess() {
        RecordingCommandRunnerPort runner = new RecordingCommandRunnerPort();
        ProcessFacade facade = processFacade(runner);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.execCmd(Map.of("args", List.of("  "))));

        assertEquals("execCmd requires 'args' as a non-empty command list", error.getMessage());
        assertNull(runner.lastSpec);
    }

    @Test
    void rejectsInvalidEnvShapeBeforeStartingProcess() {
        RecordingCommandRunnerPort runner = new RecordingCommandRunnerPort();
        ProcessFacade facade = processFacade(runner);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.execCmd(Map.of("args", List.of("echo"), "env", "FOO=bar")));

        assertEquals("execCmd 'env' must be a map when provided", error.getMessage());
        assertNull(runner.lastSpec);
    }

    @Test
    void rejectsNullEnvValueBeforeStartingProcess() {
        RecordingCommandRunnerPort runner = new RecordingCommandRunnerPort();
        ProcessFacade facade = processFacade(runner);
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("FOO", null);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("args", List.of("echo"));
        options.put("env", env);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.execCmd(options));

        assertEquals("execCmd 'env' values must not be null", error.getMessage());
        assertNull(runner.lastSpec);
    }

    @Test
    void rejectsInvalidEnvKeyBeforeStartingProcess() {
        RecordingCommandRunnerPort runner = new RecordingCommandRunnerPort();
        ProcessFacade facade = processFacade(runner);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.execCmd(Map.of("args", List.of("echo"), "env", Map.of("A=B", "value"))));

        assertEquals("execCmd 'env' keys must not contain '='", error.getMessage());
        assertNull(runner.lastSpec);
    }

    @Test
    void passesConfiguredTimeoutToRunner() {
        RecordingCommandRunnerPort runner = new RecordingCommandRunnerPort();
        ProcessFacade facade = processFacade(runner);

        facade.execCmd(Map.of("args", List.of("echo"), "timeoutSeconds", 3));

        assertEquals(Duration.ofSeconds(3), runner.lastSpec.timeout());
    }

    @Test
    void rejectsInvalidTimeoutBeforeStartingProcess() {
        RecordingCommandRunnerPort runner = new RecordingCommandRunnerPort();
        ProcessFacade facade = processFacade(runner);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.execCmd(Map.of("args", List.of("echo"), "timeoutSeconds", 0)));

        assertEquals("execCmd 'timeoutSeconds' must be positive", error.getMessage());
        assertNull(runner.lastSpec);
    }

    private static ProcessFacade processFacade(RecordingCommandRunnerPort runner) {
        return new ProcessFacade(runner, new DotenvParser(), new ApiLogFormatter(new FrameworkConfig.Logging()));
    }

    private static class RecordingCommandRunnerPort implements CommandRunnerPort {
        private ExecCommandSpec lastSpec;

        @Override
        public CommandResult run(ExecCommandSpec spec, Consumer<LogEvent> onLogLine) {
            this.lastSpec = spec;
            return new CommandResult(0, "");
        }
    }
}
