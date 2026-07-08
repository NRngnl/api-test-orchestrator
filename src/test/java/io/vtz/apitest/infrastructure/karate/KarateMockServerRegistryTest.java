package io.vtz.apitest.infrastructure.karate;

import io.vtz.apitest.domain.mock.MockServerSpec;
import io.vtz.apitest.domain.mock.RunningMockServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    private static MockServerSpec spec(String feature, int port) {
        return new MockServerSpec(feature, port, false, null, null, null, true, true, Map.of());
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
