package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.port.MockServerPort;
import io.vtz.apitest.domain.mock.MockServerSpec;
import io.vtz.apitest.domain.mock.RunningMockServer;

import java.util.List;
import java.util.Map;

public class MockServerFacade {
    private static final String DEFAULT_CERT_ENV = "ATO_MOCK_SSL_CERT_PATH";
    private static final String DEFAULT_KEY_ENV = "ATO_MOCK_SSL_KEY_PATH";

    private final MockServerPort mockServers;
    private final Map<String, String> variables;

    public MockServerFacade(MockServerPort mockServers) {
        this(mockServers, System.getenv());
    }

    MockServerFacade(MockServerPort mockServers, Map<String, String> variables) {
        this.mockServers = mockServers;
        this.variables = Map.copyOf(variables == null ? Map.of() : variables);
    }

    public RunningMockServer start(Map<String, Object> options) {
        MockServerSpec spec = toSpec(options, variables);
        stopExistingServerOnFixedPort(spec);
        return mockServers.start(spec);
    }

    public RunningMockServer start(String feature) {
        return mockServers.start(MockServerSpec.dynamicPort(feature));
    }

    public void stop(String id) {
        mockServers.stop(id);
    }

    public void stopAll() {
        mockServers.stopAll();
    }

    public List<RunningMockServer> runningServers() {
        return mockServers.runningServers();
    }

    private void stopExistingServerOnFixedPort(MockServerSpec spec) {
        if (spec.port() == 0) {
            return;
        }
        mockServers.runningServers().stream()
                .filter(server -> server.port() == spec.port())
                .map(RunningMockServer::id)
                .toList()
                .forEach(mockServers::stop);
    }

    private static MockServerSpec toSpec(Map<String, Object> options, Map<String, String> variables) {
        String feature = string(options.getOrDefault("feature", options.get("mock")));
        int port = integer(options.get("port"), 0);
        boolean ssl = bool(options.get("ssl"), false);
        String certPath = string(options.get("certPath"));
        if (certPath == null) {
            certPath = string(options.get("cert"));
        }
        String keyPath = string(options.get("keyPath"));
        if (keyPath == null) {
            keyPath = string(options.get("key"));
        }
        if (ssl) {
            if (certPath == null || certPath.isBlank()) {
                certPath = string(variables.get(DEFAULT_CERT_ENV));
            }
            if (keyPath == null || keyPath.isBlank()) {
                keyPath = string(variables.get(DEFAULT_KEY_ENV));
            }
        }
        String pathPrefix = string(options.get("pathPrefix"));
        boolean javaBridgeEnabled = bool(options.get("javaBridgeEnabled"), true);
        boolean requestExpressionsEnabled = bool(options.get("requestExpressionsEnabled"), true);
        Object args = options.get("args");
        Map<String, Object> argMap = args instanceof Map<?, ?> map
                ? map.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> String.valueOf(entry.getKey()),
                Map.Entry::getValue))
                : Map.of();
        return new MockServerSpec(
                feature,
                port,
                ssl,
                certPath,
                keyPath,
                pathPrefix,
                javaBridgeEnabled,
                requestExpressionsEnabled,
                argMap);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int integer(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static boolean bool(Object value, boolean defaultValue) {
        return value instanceof Boolean bool ? bool : defaultValue;
    }
}
