package io.vtz.apitest.domain.process;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessSpecTest {
    @Test
    void rejectsExecTimeoutBelowMillisecond() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ExecCommandSpec(List.of("echo"), null, Map.of(), Map.of(), Duration.ofNanos(1)));

        assertEquals("timeout must be at least 1 millisecond", error.getMessage());
    }

    @Test
    void rejectsApiHealthTimeoutBelowMillisecond() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> apiProcessSpec(Duration.ZERO, Duration.ofMillis(1)));

        assertEquals("healthTimeout must be at least 1 millisecond", error.getMessage());
    }

    @Test
    void rejectsApiHealthIntervalBelowMillisecond() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> apiProcessSpec(Duration.ofSeconds(1), Duration.ZERO));

        assertEquals("healthInterval must be at least 1 millisecond", error.getMessage());
    }

    private static ApiProcessSpec apiProcessSpec(Duration healthTimeout, Duration healthInterval) {
        return new ApiProcessSpec(
                List.of("echo"),
                null,
                URI.create("http://127.0.0.1:1"),
                healthTimeout,
                healthInterval,
                Map.of());
    }
}
