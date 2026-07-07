package io.vtz.apitest.domain.test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record TestRunResult(
        int featureCount,
        int featureFailedCount,
        int scenarioCount,
        int scenarioFailedCount,
        Duration duration,
        Path reportDir,
        List<String> errors,
        List<FeatureRunResult> features
) {
    public TestRunResult {
        errors = List.copyOf(errors == null ? List.of() : errors);
        features = List.copyOf(features == null ? List.of() : features);
    }

    public boolean passed() {
        return scenarioFailedCount == 0 && featureFailedCount == 0;
    }
}
