package io.vtz.apitest.interfaces.cli;

import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.log.LogFilterRule;
import io.vtz.apitest.domain.process.ApiProcessSpec;
import io.vtz.apitest.domain.test.TestRunRequest;
import io.vtz.apitest.domain.test.TestRunResult;
import io.vtz.apitest.infrastructure.config.FrameworkConfig;
import io.vtz.apitest.infrastructure.config.FrameworkConfigLoader;
import io.vtz.apitest.infrastructure.karate.KarateCoreRunner;
import io.vtz.apitest.infrastructure.log.RequestLogCorrelator;
import io.vtz.apitest.infrastructure.process.ApiProcessSupervisor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "api-test-orchestrator",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "Run API scenario tests with API monitoring, mock support, DB fixtures, and log filtering.")
public class ApiTestOrchestratorCli implements Callable<Integer> {
    @Option(names = {"-c", "--config"}, description = "YAML, JSON, or TOML config file")
    Path configPath;

    @Option(names = "--include", description = "Include log regex, repeatable")
    List<String> includePatterns = new ArrayList<>();

    @Option(names = "--exclude", description = "Exclude log regex, repeatable")
    List<String> excludePatterns = new ArrayList<>();

    @Option(names = "--level", description = "Minimum API log level: DEBUG, INFO, WARN, ERROR, ALL")
    String level;

    @Option(names = "--failed-only", description = "Buffer API logs and print the latest request logs on failure")
    boolean failedOnly;

    @Option(names = "--no-color", description = "Disable ANSI colors in API JSON log output")
    boolean noColor;

    @Option(names = "--no-api", description = "Do not start the configured API process")
    boolean noApi;

    @Parameters(arity = "0..*", description = "Scenario feature paths")
    List<String> paths = new ArrayList<>();

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ApiTestOrchestratorCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        FrameworkConfig config = configPath == null ? new FrameworkConfig() : new FrameworkConfigLoader().load(configPath);
        applyOverrides(config);
        if (configPath != null) {
            config.karate.systemProperties.put("ato.config", configPath.toAbsolutePath().toString());
        }

        LogFilterRule filterRule = config.toLogFilterRule();
        RequestLogCorrelator correlator = new RequestLogCorrelator();
        ApiLogFormatter apiLogFormatter = new ApiLogFormatter(config.logging);
        ApiProcessSpec apiSpec = noApi ? null : config.toApiProcessSpec();

        try (ApiProcessSupervisor api = new ApiProcessSupervisor()) {
            if (apiSpec != null) {
                System.out.println("[api] starting: " + String.join(" ", apiSpec.command()));
                api.start(apiSpec, event -> onApiLog(event, filterRule, correlator, apiLogFormatter, config.logging.failedOnly), line -> {
                    if (!config.logging.failedOnly) {
                        System.err.println("[api:stderr] " + line);
                    }
                });
                if (!api.awaitReady(apiSpec)) {
                    System.err.println("[api] failed to become ready: " + apiSpec.healthUrl());
                    return 1;
                }
                System.out.println(ProgressLineFormatter.apiReady(apiSpec.healthUrl().toString()));
            }

            TestRunRequest request = config.toTestRunRequest(paths);
            TestRunResult result = new KarateCoreRunner().run(request);
            result.features().forEach(feature -> System.out.println(ProgressLineFormatter.featureLine(feature)));
            if (!result.passed() && config.logging.failedOnly) {
                System.out.println("[api] latest request logs:");
                correlator.lastRequestLogs().forEach(event -> System.out.println(apiLogFormatter.indentedApiLine(event)));
            }
            printSummary(result);
            return result.passed() ? 0 : 1;
        }
    }

    void applyOverrides(FrameworkConfig config) {
        if (!includePatterns.isEmpty()) {
            config.logging.includePatterns = includePatterns;
        }
        if (!excludePatterns.isEmpty()) {
            config.logging.excludePatterns = excludePatterns;
        }
        if (level != null && !level.isBlank()) {
            config.logging.level = level;
        }
        if (failedOnly) {
            config.logging.failedOnly = true;
        }
        if (noColor) {
            config.logging.colors = false;
        }
    }

    private static void onApiLog(
            LogEvent event,
            LogFilterRule filterRule,
            RequestLogCorrelator correlator,
            ApiLogFormatter apiLogFormatter,
            boolean failedOnly) {
        correlator.accept(event);
        if (!failedOnly && filterRule.includes(event)) {
            System.out.println(apiLogFormatter.apiLine(event));
        }
    }

    private static void printSummary(TestRunResult result) {
        System.out.printf(
                "[engine] features=%d failed=%d scenarios=%d failed=%d duration=%s report=%s%n",
                result.featureCount(),
                result.featureFailedCount(),
                result.scenarioCount(),
                result.scenarioFailedCount(),
                result.duration(),
                result.reportDir());
        if (!result.errors().isEmpty()) {
            result.errors().forEach(error -> System.out.println("[engine:error] " + error));
        }
    }
}
