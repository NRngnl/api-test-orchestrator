package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.domain.mock.RunningMockServer;
import io.vtz.apitest.infrastructure.config.FrameworkConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiTestOrchestratorTest {
    @Test
    void sharesMockServerRegistryAcrossInstancesSoFixedPortsCanBeReplaced() throws IOException {
        int port = availablePort();
        String feature = Path.of("src/test/resources/mock/simple-mock.feature").toAbsolutePath().toString();
        Map<String, Object> mockOptions = Map.of("feature", feature, "port", port);

        try (ApiTestOrchestrator first = new ApiTestOrchestrator(new FrameworkConfig());
             ApiTestOrchestrator second = new ApiTestOrchestrator(new FrameworkConfig())) {
            first.mocks().start(mockOptions);
            RunningMockServer replacement = second.mocks().start(mockOptions);

            assertEquals(port, replacement.port());
            assertEquals(1, second.mocks().runningServers().size());
            assertEquals(replacement.id(), second.mocks().runningServers().getFirst().id());
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
