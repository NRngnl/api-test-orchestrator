package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.service.ChecksumService;
import io.vtz.apitest.application.service.DateFactory;
import io.vtz.apitest.application.service.DotenvParser;
import io.vtz.apitest.application.service.FixtureRowPreparer;
import io.vtz.apitest.application.service.IdentifierFactory;
import io.vtz.apitest.domain.db.DatabaseTarget;
import io.vtz.apitest.infrastructure.config.FrameworkConfig;
import io.vtz.apitest.infrastructure.config.FrameworkConfigLoader;
import io.vtz.apitest.infrastructure.db.JdbcDatabaseGateway;
import io.vtz.apitest.infrastructure.karate.KarateMockServerRegistry;
import io.vtz.apitest.infrastructure.process.ProcessCommandRunner;
import io.vtz.apitest.interfaces.cli.ApiLogFormatter;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ApiTestOrchestrator implements AutoCloseable {
    private static final KarateMockServerRegistry PROCESS_MOCK_SERVERS = new KarateMockServerRegistry();

    private final FrameworkConfig config;
    private final Map<String, DatabaseFacade> databaseFacades;
    private final MockServerFacade mockServerFacade;
    private final DateFactory dateFactory;
    private final IdentifierFactory identifierFactory;
    private final ChecksumService checksumService;
    private final ProcessFacade processFacade;
    private final FeatureBridge featureBridge;

    public ApiTestOrchestrator(FrameworkConfig config) {
        this.config = config;
        this.identifierFactory = new IdentifierFactory();
        this.databaseFacades = createDatabaseFacades(config, identifierFactory);
        this.mockServerFacade = new MockServerFacade(PROCESS_MOCK_SERVERS);
        this.dateFactory = new DateFactory();
        this.checksumService = new ChecksumService();
        this.processFacade = new ProcessFacade(
                new ProcessCommandRunner(),
                new DotenvParser(),
                new ApiLogFormatter(config.logging));
        this.featureBridge = new FeatureBridge(
                databaseFacades,
                config.defaultDatabaseName(),
                mockServerFacade,
                dateFactory,
                identifierFactory,
                checksumService,
                processFacade);
    }

    public static ApiTestOrchestrator fromEnvironment() {
        String configPath = System.getProperty("ato.config");
        if (configPath == null || configPath.isBlank()) {
            configPath = System.getenv("ATO_CONFIG");
        }
        if (configPath != null && !configPath.isBlank()) {
            return fromConfig(configPath);
        }
        FrameworkConfig config = new FrameworkConfig();
        String jdbcUrl = System.getenv("ATO_JDBC_URL");
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            config.database.jdbcUrl = jdbcUrl;
            config.database.username = System.getenv("ATO_DB_USERNAME");
            config.database.password = System.getenv("ATO_DB_PASSWORD");
        }
        return new ApiTestOrchestrator(config);
    }

    public static ApiTestOrchestrator fromConfig(String path) {
        FrameworkConfig config = new FrameworkConfigLoader().load(Path.of(path));
        return new ApiTestOrchestrator(config);
    }

    public FrameworkConfig config() {
        return config;
    }

    public DatabaseFacade db() {
        DatabaseFacade databaseFacade = dbOrNull();
        if (databaseFacade == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return databaseFacade;
    }

    public DatabaseFacade db(String name) {
        DatabaseFacade databaseFacade = databaseFacades.get(name);
        if (databaseFacade == null) {
            throw new IllegalArgumentException("Database is not configured: " + name);
        }
        return databaseFacade;
    }

    public Set<String> dbNames() {
        return databaseFacades.keySet();
    }

    public MockServerFacade mocks() {
        return mockServerFacade;
    }

    public DateFactory date() {
        return dateFactory;
    }

    public IdentifierFactory ids() {
        return identifierFactory;
    }

    public ChecksumService checksum() {
        return checksumService;
    }

    public ProcessFacade process() {
        return processFacade;
    }

    public FeatureBridge helpers() {
        return featureBridge;
    }

    @Override
    public void close() {
        mockServerFacade.stopAll();
        databaseFacades.values().forEach(DatabaseFacade::close);
    }

    private DatabaseFacade dbOrNull() {
        DatabaseFacade databaseFacade = databaseFacades.get(config.defaultDatabaseName());
        return databaseFacade == null && !databaseFacades.isEmpty() ? databaseFacades.values().iterator().next() : databaseFacade;
    }

    private static Map<String, DatabaseFacade> createDatabaseFacades(FrameworkConfig config, IdentifierFactory identifierFactory) {
        Map<String, DatabaseFacade> facades = new LinkedHashMap<>();
        for (Map.Entry<String, DatabaseTarget> entry : config.toDatabaseTargets().entrySet()) {
            facades.put(entry.getKey(), new DatabaseFacade(
                    new JdbcDatabaseGateway(entry.getValue()),
                    new FixtureRowPreparer(config.toFixturePolicy(entry.getKey()), identifierFactory::randomString)));
        }
        return Collections.unmodifiableMap(facades);
    }
}
