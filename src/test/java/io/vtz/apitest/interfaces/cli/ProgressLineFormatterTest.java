package io.vtz.apitest.interfaces.cli;

import io.vtz.apitest.domain.test.FeatureRunResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressLineFormatterTest {
    @Test
    void formatsApiReadyLineForParallelRunnerParsing() {
        assertEquals(
                "✅ API is ready: http://localhost:1323/",
                ProgressLineFormatter.apiReady("http://localhost:1323/"));
    }

    @Test
    void formatsFeatureProgressLinesForParallelRunnerParsing() {
        FeatureRunResult result = new FeatureRunResult("sample.feature", 3, 2, 1, Duration.ofMillis(1234));

        assertEquals(
                "❌ sample.feature scenarios: 3 | passed: 2 | failed: 1 | time: 1.234",
                ProgressLineFormatter.featureLine(result));
    }
}
