package io.vtz.apitest.interfaces.cli;

import io.vtz.apitest.domain.test.FeatureRunResult;

import java.util.Locale;

final class ProgressLineFormatter {
    private ProgressLineFormatter() {
    }

    static String apiReady(String healthUrl) {
        return "✅ API is ready: " + healthUrl;
    }

    static String featureLine(FeatureRunResult result) {
        String status = result.passed() ? "✅" : "❌";
        return String.format(
                Locale.ROOT,
                "%s %s scenarios: %d | passed: %d | failed: %d | time: %.3f",
                status,
                result.name(),
                result.scenarioCount(),
                result.scenarioPassedCount(),
                result.scenarioFailedCount(),
                result.duration().toMillis() / 1000.0);
    }
}
