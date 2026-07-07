package io.vtz.apitest.domain.test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record TestRunRequest(
        List<String> paths,
        List<String> tags,
        Path workingDir,
        Path configDir,
        Path outputDir,
        int threads,
        boolean outputHtml,
        boolean outputJsonLines,
        boolean outputCucumberJson,
        boolean outputJunitXml,
        String consoleLevel,
        Map<String, String> systemProperties
) {
    public TestRunRequest {
        paths = List.copyOf(paths == null || paths.isEmpty() ? List.of(".") : paths);
        tags = List.copyOf(tags == null ? List.of() : tags);
        systemProperties = Map.copyOf(systemProperties == null ? Map.of() : systemProperties);
        threads = Math.max(1, threads);
    }
}
