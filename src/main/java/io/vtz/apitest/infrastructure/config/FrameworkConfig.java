package io.vtz.apitest.infrastructure.config;

import io.vtz.apitest.domain.db.DatabaseTarget;
import io.vtz.apitest.domain.db.FixturePolicy;
import io.vtz.apitest.domain.log.LogFilterRule;
import io.vtz.apitest.domain.log.LogLevel;
import io.vtz.apitest.domain.process.ApiProcessSpec;
import io.vtz.apitest.domain.test.TestRunRequest;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class FrameworkConfig {
    public Api api = new Api();
    public Karate karate = new Karate();
    public Database database = new Database();
    public Logging logging = new Logging();

    public ApiProcessSpec toApiProcessSpec() {
        if (api.command == null || api.command.isEmpty()) {
            return null;
        }
        return new ApiProcessSpec(
                api.command,
                path(api.workingDir),
                URI.create(api.healthUrl),
                Duration.ofSeconds(api.healthTimeoutSeconds),
                Duration.ofMillis(api.healthIntervalMillis),
                api.environment);
    }

    public TestRunRequest toTestRunRequest(List<String> overridePaths) {
        List<String> paths = overridePaths == null || overridePaths.isEmpty() ? karate.defaultPaths : overridePaths;
        return new TestRunRequest(
                paths,
                karate.tags,
                path(karate.workingDir),
                path(karate.configDir),
                path(karate.outputDir),
                karate.threads,
                karate.outputHtml,
                karate.outputJsonLines,
                karate.outputCucumberJson,
                karate.outputJunitXml,
                karate.consoleLevel,
                karate.systemProperties);
    }

    public DatabaseTarget toDatabaseTarget() {
        Map<String, DatabaseTarget> targets = toDatabaseTargets();
        if (targets.isEmpty()) {
            return null;
        }
        DatabaseTarget defaultTarget = targets.get(defaultDatabaseName());
        return defaultTarget == null ? targets.values().iterator().next() : defaultTarget;
    }

    public Map<String, DatabaseTarget> toDatabaseTargets() {
        Map<String, DatabaseTarget> targets = new LinkedHashMap<>();
        if (hasJdbcTarget(database)) {
            targets.put(defaultDatabaseName(), toDatabaseTarget(database));
        }
        database.targets.forEach((name, settings) -> {
            if (hasJdbcTarget(settings)) {
                targets.put(name, toDatabaseTarget(settings));
            }
        });
        return targets;
    }

    public FixturePolicy toFixturePolicy() {
        return toFixturePolicy(defaultDatabaseName());
    }

    public FixturePolicy toFixturePolicy(String databaseName) {
        DatabaseSettings settings = database.targets.get(databaseName);
        if (settings == null) {
            settings = database;
        }
        Map<String, FixturePolicy.GeneratedStringColumn> generatedColumns = generatedColumns(settings);
        return new FixturePolicy(
                settings.defaults,
                settings.tableDefaults,
                generatedColumns);
    }

    public String defaultDatabaseName() {
        return database.defaultName == null || database.defaultName.isBlank() ? "default" : database.defaultName;
    }

    public LogFilterRule toLogFilterRule() {
        return new LogFilterRule(
                LogLevel.parse(logging.level),
                compile(logging.includePatterns),
                compile(logging.excludePatterns));
    }

    private static Path path(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static List<Pattern> compile(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(Pattern::compile).toList();
    }

    private static boolean hasJdbcTarget(DatabaseSettings settings) {
        return settings != null && settings.jdbcUrl != null && !settings.jdbcUrl.isBlank();
    }

    private static DatabaseTarget toDatabaseTarget(DatabaseSettings settings) {
        return new DatabaseTarget(settings.jdbcUrl, settings.username, settings.password);
    }

    private static Map<String, FixturePolicy.GeneratedStringColumn> generatedColumns(DatabaseSettings settings) {
        Map<String, FixturePolicy.GeneratedStringColumn> generatedColumns = new LinkedHashMap<>();
        settings.generatedColumns.forEach((column, generated) ->
                generatedColumns.put(column, new FixturePolicy.GeneratedStringColumn(generated.tables, generated.length)));
        return generatedColumns;
    }

    private static Map<String, String> defaultJsonLogColors() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("DEBUG", "cyan");
        colors.put("INFO", "green");
        colors.put("WARN", "yellow");
        colors.put("ERROR", "red");
        return colors;
    }

    public static class Api {
        public List<String> command = new ArrayList<>();
        public String workingDir;
        public String healthUrl = "http://localhost:1323/";
        public long healthTimeoutSeconds = 30;
        public long healthIntervalMillis = 1000;
        public Map<String, String> environment = new LinkedHashMap<>();
    }

    public static class Karate {
        public String workingDir;
        public String configDir;
        public String outputDir = "target/karate-reports";
        public int threads = 1;
        public boolean outputHtml = true;
        public boolean outputJsonLines = true;
        public boolean outputCucumberJson;
        public boolean outputJunitXml;
        public String consoleLevel = "info";
        public List<String> defaultPaths = new ArrayList<>(List.of("src/test/java"));
        public List<String> tags = new ArrayList<>();
        public Map<String, String> systemProperties = new LinkedHashMap<>();
    }

    public static class Database extends DatabaseSettings {
        public String defaultName = "default";
        public Map<String, DatabaseSettings> targets = new LinkedHashMap<>();
    }

    public static class DatabaseSettings {
        public String jdbcUrl;
        public String username;
        public String password;
        public Map<String, Object> defaults = new LinkedHashMap<>();
        public Map<String, Map<String, Object>> tableDefaults = new LinkedHashMap<>();
        public Map<String, GeneratedColumn> generatedColumns = new LinkedHashMap<>();
    }

    public static class GeneratedColumn {
        public Set<String> tables = new LinkedHashSet<>();
        public int length = 16;
    }

    public static class Logging {
        public String level = "ALL";
        public List<String> includePatterns = new ArrayList<>();
        public List<String> excludePatterns = new ArrayList<>();
        public boolean failedOnly;
        public boolean sqlStats = true;
        public boolean colors = true;
        public Map<String, String> jsonLogColors = defaultJsonLogColors();
    }
}
