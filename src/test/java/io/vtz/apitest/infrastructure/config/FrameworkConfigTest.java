package io.vtz.apitest.infrastructure.config;

import io.vtz.apitest.domain.db.FixturePolicy;
import io.vtz.apitest.domain.db.DatabaseTarget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameworkConfigTest {
    @Test
    void createsConfiguredFixturePolicy() {
        FrameworkConfig config = new FrameworkConfig();
        config.database.defaults = Map.of("tenant_id", 7);
        config.database.tableDefaults = Map.of(
                "cases", Map.of("status", "OPEN", "owner_id", 1),
                "users", Map.of("active", true));
        FrameworkConfig.GeneratedColumn generatedColumn = new FrameworkConfig.GeneratedColumn();
        generatedColumn.tables = Set.of("cases");
        generatedColumn.length = 12;
        config.database.generatedColumns = Map.of("fixture_key", generatedColumn);

        FixturePolicy policy = config.toFixturePolicy();

        assertEquals(Map.of("fixture_key", "generated-value"), policy.generatedValuesFor("cases", length -> {
            assertEquals(12, length);
            return "generated-value";
        }));
        assertTrue(policy.generatedValuesFor("users", length -> "unused").isEmpty());
        assertEquals(Map.of("tenant_id", 7, "status", "OPEN", "owner_id", 1), policy.defaultsFor("cases"));
        assertEquals(Map.of("tenant_id", 7, "active", true), policy.defaultsFor("users"));
    }

    @Test
    void createsNamedDatabaseTargetsAndFixturePolicies() {
        FrameworkConfig config = new FrameworkConfig();
        config.database.defaultName = "primary";
        FrameworkConfig.DatabaseSettings primary = new FrameworkConfig.DatabaseSettings();
        primary.jdbcUrl = "jdbc:mysql://primary/test_db";
        primary.username = "primary-user";
        primary.password = "primary-password";
        primary.defaults = Map.of("tenant_id", 7);
        FrameworkConfig.DatabaseSettings audit = new FrameworkConfig.DatabaseSettings();
        audit.jdbcUrl = "jdbc:mysql://audit/test_db";
        audit.username = "audit-user";
        audit.password = "audit-password";
        audit.tableDefaults = Map.of("events", Map.of("source", "feature"));
        config.database.targets = new LinkedHashMap<>();
        config.database.targets.put("primary", primary);
        config.database.targets.put("audit", audit);

        Map<String, DatabaseTarget> targets = config.toDatabaseTargets();

        assertEquals("jdbc:mysql://primary/test_db", targets.get("primary").jdbcUrl());
        assertEquals("audit-user", targets.get("audit").username());
        assertEquals("primary", config.defaultDatabaseName());
        assertEquals(Map.of("tenant_id", 7), config.toFixturePolicy("primary").defaultsFor("cases"));
        assertEquals(Map.of("source", "feature"), config.toFixturePolicy("audit").defaultsFor("events"));
    }
}
