package io.vtz.apitest.infrastructure.karate;

import io.vtz.apitest.application.port.KarateRunnerPort;
import io.vtz.apitest.domain.test.FeatureRunResult;
import io.vtz.apitest.domain.test.TestRunRequest;
import io.vtz.apitest.domain.test.TestRunResult;
import io.karatelabs.core.FeatureResult;
import io.karatelabs.core.Runner;
import io.karatelabs.core.SuiteResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class KarateCoreRunner implements KarateRunnerPort {
    @Override
    public TestRunResult run(TestRunRequest request) {
        Runner.Builder builder = Runner.builder()
                .path(request.paths())
                .outputHtmlReport(request.outputHtml())
                .outputJsonLines(request.outputJsonLines())
                .outputCucumberJson(request.outputCucumberJson())
                .outputJunitXml(request.outputJunitXml())
                .outputConsoleSummary(true);

        if (!request.tags().isEmpty()) {
            builder.tags(request.tags().toArray(String[]::new));
        }
        if (request.workingDir() != null) {
            builder.workingDir(request.workingDir());
        }
        if (request.configDir() != null) {
            builder.configDir(request.configDir().toString());
        }
        if (request.outputDir() != null) {
            builder.outputDir(request.outputDir());
        }
        if (request.consoleLevel() != null && !request.consoleLevel().isBlank()) {
            builder.consoleLevel(request.consoleLevel());
        }
        request.systemProperties().forEach(builder::systemProperty);

        SuiteResult result = builder.parallel(request.threads());
        Path reportDir = result.getReportDir();
        return new TestRunResult(
                result.getFeatureCount(),
                result.getFeatureFailedCount(),
                result.getScenarioCount(),
                result.getScenarioFailedCount(),
                Duration.ofMillis(result.getDurationMillis()),
                reportDir,
                result.getErrors(),
                featureResults(result));
    }

    private static List<FeatureRunResult> featureResults(SuiteResult result) {
        return result.getFeatureResults().stream()
                .map(KarateCoreRunner::featureResult)
                .toList();
    }

    private static FeatureRunResult featureResult(FeatureResult result) {
        return new FeatureRunResult(
                result.getDisplayName(),
                result.getScenarioCount(),
                result.getPassedCount(),
                result.getFailedCount(),
                Duration.ofMillis(result.getDurationMillis()));
    }
}
