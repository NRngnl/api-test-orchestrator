package io.vtz.apitest.infrastructure.process;

import io.vtz.apitest.domain.process.ApiProcessSpec;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiProcessSupervisorTest {
    @Test
    void slowStdoutSinkDoesNotBlockApiOutputDrain() {
        assertTimeout(Duration.ofSeconds(4), () -> {
            ApiProcessSupervisor supervisor = new ApiProcessSupervisor();
            try {
                ApiProcessSpec spec = spec(List.of("sh", "-c", "yes line | head -n 200000"), Duration.ofSeconds(5));
                AtomicBoolean firstLine = new AtomicBoolean(true);

                supervisor.start(spec, event -> {
                    if (firstLine.getAndSet(false)) {
                        try {
                            Thread.sleep(10_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }, line -> {
                });

                waitUntilNotRunning(supervisor, Duration.ofSeconds(2));

                assertFalse(supervisor.isRunning());
                assertEquals(0, supervisor.stop());
            } finally {
                supervisor.stop();
            }
        });
    }

    @Test
    void awaitReadyReturnsPromptlyWhenProcessExits() {
        assertTimeout(Duration.ofSeconds(3), () -> {
            ApiProcessSupervisor supervisor = new ApiProcessSupervisor();
            try {
                ApiProcessSpec spec = spec(List.of("sh", "-c", "exit 7"), Duration.ofSeconds(10));
                supervisor.start(spec, event -> {
                }, line -> {
                });

                long started = System.nanoTime();
                boolean ready = supervisor.awaitReady(spec);
                long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

                assertFalse(ready);
                assertFalse(supervisor.isRunning());
                assertTrue(elapsedMillis < 2_000, "readiness should stop before the full health timeout");
            } finally {
                supervisor.stop();
            }
        });
    }

    @Test
    void awaitReadyReturnsPromptlyWhenInterrupted() {
        ApiProcessSupervisor supervisor = new ApiProcessSupervisor();
        ApiProcessSpec spec = spec(List.of("sh", "-c", "sleep 1"), Duration.ofSeconds(10));

        Thread.currentThread().interrupt();
        try {
            assertFalse(supervisor.awaitReady(spec));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static ApiProcessSpec spec(List<String> command, Duration timeout) {
        return new ApiProcessSpec(
                command,
                null,
                URI.create("http://127.0.0.1:1"),
                timeout,
                Duration.ofMillis(25),
                Map.of());
    }

    private static void waitUntilNotRunning(ApiProcessSupervisor supervisor, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!supervisor.isRunning()) {
                return;
            }
            Thread.sleep(25);
        }
    }
}
