package io.vtz.apitest.domain.mock;

import java.util.Map;

public record MockServerSpec(
        String feature,
        int port,
        boolean ssl,
        String certPath,
        String keyPath,
        String pathPrefix,
        boolean javaBridgeEnabled,
        boolean requestExpressionsEnabled,
        Map<String, Object> args
) {
    public MockServerSpec {
        if (feature == null || feature.isBlank()) {
            throw new IllegalArgumentException("feature is required");
        }
        args = Map.copyOf(args == null ? Map.of() : args);
    }

    public static MockServerSpec dynamicPort(String feature) {
        return new MockServerSpec(feature, 0, false, null, null, null, true, true, Map.of());
    }
}
