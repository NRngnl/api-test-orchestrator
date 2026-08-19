package io.vtz.apitest.infrastructure.process;

import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.process.CommandResult;
import io.vtz.apitest.domain.process.ExecCommandSpec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessCommandRunnerTest {
    @Test
    void mergesEnvStreamsLinesAndReturnsExitCodeWithCombinedOutput() {
        List<LogEvent> streamed = new ArrayList<>();
        ExecCommandSpec spec = new ExecCommandSpec(
                List.of("sh", "-c",
                        "echo out; echo err 1>&2; echo home=[$HOME]; echo fileonly=$FILE_ONLY; echo foo=$FOO; exit 3"),
                null,
                Map.of("HOME", "should-not-win", "FILE_ONLY", "file-value"),
                Map.of("FOO", "override-wins"));

        CommandResult result = new ProcessCommandRunner().run(spec, streamed::add);

        assertEquals(3, result.exitCode());
        assertTrue(result.output().contains("out"), "stdout captured");
        assertTrue(result.output().contains("err"), "stderr merged via redirectErrorStream");
        assertTrue(result.output().contains("fileonly=file-value"), "file default applied when absent from parent env");
        assertFalse(result.output().contains("home=[should-not-win]"), "file default must not overwrite inherited parent env");
        assertTrue(result.output().contains("foo=override-wins"), "override always wins");
        assertTrue(streamed.size() >= 5, "each output line streamed as a LogEvent");
    }

    @Test
    void slowLogConsumerDoesNotBlockProcessOutputDrain() {
        assertTimeout(Duration.ofSeconds(4), () -> {
            ExecCommandSpec spec = new ExecCommandSpec(
                    List.of("sh", "-c", "echo first; echo second; echo third"),
                    null,
                    Map.of(),
                    Map.of());
            AtomicBoolean firstLog = new AtomicBoolean(true);

            CommandResult result = new ProcessCommandRunner().run(spec, event -> {
                if (firstLog.getAndSet(false)) {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            assertEquals(0, result.exitCode());
            assertTrue(result.output().contains("first"));
            assertTrue(result.output().contains("third"));
        });
    }

    @Test
    void stdinReadingCommandDoesNotBlockForever() {
        assertTimeout(Duration.ofSeconds(4), () -> {
            ExecCommandSpec spec = new ExecCommandSpec(
                    List.of("sh", "-c", "read ignored; echo after-eof"),
                    null,
                    Map.of(),
                    Map.of(),
                    Duration.ofSeconds(2));

            CommandResult result = new ProcessCommandRunner().run(spec, event -> {
            });

            assertEquals(0, result.exitCode());
            assertTrue(result.output().contains("after-eof"));
        });
    }

    @Test
    void commandTimeoutKillsNonTerminatingProcess() {
        ExecCommandSpec spec = new ExecCommandSpec(
                List.of("sh", "-c", "tail -f /dev/null"),
                null,
                Map.of(),
                Map.of(),
                Duration.ofSeconds(1));

        IllegalStateException error = assertTimeout(
                Duration.ofSeconds(4),
                () -> assertThrows(IllegalStateException.class, () -> new ProcessCommandRunner().run(spec, event -> {
                })));

        assertTrue(error.getMessage().contains("Command timed out after"));
    }
}
