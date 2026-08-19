package io.vtz.apitest.infrastructure.karate;

import io.vtz.apitest.domain.mock.MockServerSpec;
import io.vtz.apitest.domain.mock.RunningMockServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class KarateMockServerRegistryTest {
    @Test
    void releasesExistingFixedPortBeforeStartingReplacement() throws IOException {
        int port = availablePort();
        String feature = Path.of("src/test/resources/mock/simple-mock.feature").toAbsolutePath().toString();
        KarateMockServerRegistry registry = new KarateMockServerRegistry();

        try {
            RunningMockServer first = registry.start(spec(feature, port));
            RunningMockServer replacement = registry.start(spec(feature, port));

            assertEquals(port, replacement.port());
            assertNotEquals(first.id(), replacement.id());
            assertEquals(1, registry.runningServers().size());
            assertEquals(replacement.id(), registry.runningServers().getFirst().id());
        } finally {
            registry.stopAll();
        }
    }

    @Test
    void startFailsFastWhenPortHasActiveListener() throws IOException {
        int port = availablePort();
        String feature = Path.of("src/test/resources/mock/simple-mock.feature").toAbsolutePath().toString();
        KarateMockServerRegistry registry = new KarateMockServerRegistry();

        // An active listener is a permanent conflict, not a transient TIME_WAIT: start() must fail fast
        // (a second SO_REUSEADDR probe still can't bind over it) instead of polling the whole bind-wait
        // budget. The classifier works for a listener on ANY local interface, not just loopback.
        try (ServerSocket blocker = new ServerSocket(port)) {
            long startNanos = System.nanoTime();
            IllegalStateException error =
                    assertThrows(IllegalStateException.class, () -> registry.start(spec(feature, port)));
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

            assertTrue(error.getMessage().contains("active listener"), error.getMessage());
            assertTrue(elapsedMillis < 5_000, "start() should fail fast, took " + elapsedMillis + "ms");
            assertEquals(0, registry.runningServers().size());
        } finally {
            registry.stopAll();
        }
    }

    @Test
    void concurrentStartsOnSamePortLeaveExactlyOneServer() throws Exception {
        int port = availablePort();
        String feature = Path.of("src/test/resources/mock/simple-mock.feature").toAbsolutePath().toString();
        KarateMockServerRegistry registry = new KarateMockServerRegistry();

        int threadCount = 4;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<RunningMockServer>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return registry.start(spec(feature, port));
                }));
            }
            for (Future<RunningMockServer> future : futures) {
                future.get();
            }
            // Per-port serialization + replace semantics: never two live servers on the same port.
            assertEquals(1, registry.runningServers().size());
            assertEquals(port, registry.runningServers().getFirst().port());
        } finally {
            pool.shutdownNow();
            registry.stopAll();
        }
    }

    @Test
    void setThenGetVariableRoundTrips() throws IOException {
        int port = availablePort();
        String feature = Path.of("src/test/resources/mock/simple-mock.feature").toAbsolutePath().toString();
        KarateMockServerRegistry registry = new KarateMockServerRegistry();

        try {
            registry.start(spec(feature, port));

            registry.setVariable(port, "mockMode", "fail");

            assertEquals("fail", registry.getVariable(port, "mockMode"));
        } finally {
            registry.stopAll();
        }
    }

    @Test
    void timeWaitPortIsNotClassifiedAsActiveListener() throws IOException {
        // Reproduce a real server-side TIME_WAIT: accept a connection, then have the SERVER close first.
        int port;
        try (ServerSocket server = new ServerSocket(0)) {
            port = server.getLocalPort();
            try (Socket client = new Socket("127.0.0.1", port)) {
                Socket accepted = server.accept();
                accepted.close();   // server is the active closer → its side enters TIME_WAIT on `port`
            }
        }
        // When TIME_WAIT is present, a plain bind (no SO_REUSEADDR, like Karate) is refused, yet there
        // is NO active listener — so bindWhenPortFree() must POLL, not fail fast. Guard with an
        // assumption so timing/platforms that free the port immediately skip rather than flake.
        assumeFalse(KarateMockServerRegistry.isPortFree(port), "no TIME_WAIT captured on this platform");
        assertFalse(KarateMockServerRegistry.hasActiveListener(port),
                "TIME_WAIT must not be misclassified as an active listener");
    }

    private static MockServerSpec spec(String feature, int port) {
        return new MockServerSpec(feature, port, false, null, null, null, true, true, Map.of());
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
