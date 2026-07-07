package io.vtz.apitest.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameworkConfigLoaderTest {
    @Test
    void substitutesVariablesBeforeParsingConfig() throws Exception {
        Path configPath = Files.createTempFile("api-test-orchestrator", ".yml");
        Files.writeString(configPath, """
                database:
                  jdbcUrl: "${DB_URL}"
                  username: "${DB_USER}"
                  password: "${DB_PASSWORD}"
                  defaults:
                    tenant_id: ${TENANT_ID}
                """);

        FrameworkConfig config = new FrameworkConfigLoader(Map.of(
                "DB_URL", "jdbc:mysql://localhost:3306/test_db",
                "DB_USER", "root",
                "DB_PASSWORD", "secret",
                "TENANT_ID", "7")).load(configPath);

        assertEquals("jdbc:mysql://localhost:3306/test_db", config.database.jdbcUrl);
        assertEquals("root", config.database.username);
        assertEquals("secret", config.database.password);
        assertEquals(7, config.database.defaults.get("tenant_id"));
    }

    @Test
    void loadsJsonLogColorConfiguration() throws Exception {
        Path configPath = Files.createTempFile("api-test-orchestrator", ".yml");
        Files.writeString(configPath, """
                logging:
                  colors: true
                  jsonLogColors:
                    DEBUG: cyan
                    INFO: green
                    WARN: yellow
                    ERROR: magenta
                """);

        FrameworkConfig config = new FrameworkConfigLoader(Map.of()).load(configPath);

        assertTrue(config.logging.colors);
        assertEquals("cyan", config.logging.jsonLogColors.get("DEBUG"));
        assertEquals("green", config.logging.jsonLogColors.get("INFO"));
        assertEquals("yellow", config.logging.jsonLogColors.get("WARN"));
        assertEquals("magenta", config.logging.jsonLogColors.get("ERROR"));
    }
}
