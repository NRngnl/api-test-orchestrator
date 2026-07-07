package io.vtz.apitest.domain.test;

import java.time.Duration;

public record FeatureRunResult(
        String name,
        int scenarioCount,
        int scenarioPassedCount,
        int scenarioFailedCount,
        Duration duration
) {
    public boolean passed() {
        return scenarioFailedCount == 0;
    }
}
