package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.port.MockServerPort;
import io.vtz.apitest.domain.mock.MockServerSpec;
import io.vtz.apitest.domain.mock.RunningMockServer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockServerFacadeTest {
    @Test
    void mapOptionsDefaultToV2FriendlyMockFlags() {
        RecordingMockServerPort port = new RecordingMockServerPort();
        MockServerFacade facade = new MockServerFacade(port);

        facade.start(Map.of("mock", "classpath:aws/sqs-success.feature", "port", 39992));

        assertEquals("classpath:aws/sqs-success.feature", port.lastSpec.feature());
        assertEquals(39992, port.lastSpec.port());
        assertTrue(port.lastSpec.javaBridgeEnabled());
        assertTrue(port.lastSpec.requestExpressionsEnabled());
    }

    @Test
    void stopsExistingServerOnSameFixedPortBeforeStartingReplacement() {
        RecordingMockServerPort port = new RecordingMockServerPort();
        port.runningServers = List.of(new RunningMockServer(
                "old-server",
                "classpath:old.feature",
                39992,
                true,
                "https://localhost:39992"));
        MockServerFacade facade = new MockServerFacade(port);

        facade.start(Map.of("mock", "classpath:new.feature", "port", 39992, "ssl", true));

        assertEquals(List.of("stop:old-server", "start:39992"), port.events);
        assertEquals("classpath:new.feature", port.lastSpec.feature());
    }

    private static class RecordingMockServerPort implements MockServerPort {
        private MockServerSpec lastSpec;
        private List<RunningMockServer> runningServers = List.of();
        private final List<String> events = new ArrayList<>();

        @Override
        public RunningMockServer start(MockServerSpec spec) {
            this.lastSpec = spec;
            events.add("start:" + spec.port());
            return new RunningMockServer("id", spec.feature(), 0, false, "http://localhost");
        }

        @Override
        public void stop(String id) {
            events.add("stop:" + id);
        }

        @Override
        public void stopAll() {
        }

        @Override
        public List<RunningMockServer> runningServers() {
            return runningServers;
        }
    }
}
