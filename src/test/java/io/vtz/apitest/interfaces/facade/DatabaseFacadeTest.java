package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.port.DatabasePort;
import io.vtz.apitest.application.service.FixtureRowPreparer;
import io.vtz.apitest.domain.db.FixturePolicy;
import io.vtz.apitest.domain.db.InsertResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseFacadeTest {
    @Test
    void insertSafePreparesFixtureRowsBeforeCallingDatabase() {
        RecordingDatabasePort database = new RecordingDatabasePort();
        DatabaseFacade facade = new DatabaseFacade(
                database,
                new FixtureRowPreparer(new FixturePolicy(
                        Map.of("tenant_id", 7),
                        Map.of("cases", Map.of("status", "OPEN")),
                        Map.of("fixture_key", new FixturePolicy.GeneratedStringColumn(Set.of("cases"), 16))),
                        length -> "fixed-fixture-key"));

        Map<String, Object> inserted = facade.insertSafe(
                "cases",
                Map.of("business_date", "2026-07-07"),
                List.of("tenant_id"));

        assertEquals("cases", database.table);
        assertEquals(List.of(), database.ignoreKeys);
        assertEquals("fixed-fixture-key", database.row.get("fixture_key"));
        assertEquals("OPEN", database.row.get("status"));
        assertFalse(database.row.containsKey("tenant_id"));
        assertEquals("2026-07-07", inserted.get("business_date"));
    }

    @Test
    void insertSafeNormalizesSyntheticGeneratedKeyLabelToIdOnly() {
        RecordingDatabasePort database = new RecordingDatabasePort();
        database.generatedKeys = Map.of("GENERATED_KEY", 123);
        database.includeGeneratedKeysInRow = true;
        DatabaseFacade facade = new DatabaseFacade(database);

        Map<String, Object> inserted = facade.insertSafe("cases", Map.of("status", "OPEN"));

        assertEquals(123, inserted.get("id"));
        assertFalse(inserted.containsKey("GENERATED_KEY"));
    }

    private static class RecordingDatabasePort implements DatabasePort {
        private String table;
        private Map<String, Object> row;
        private List<String> ignoreKeys;
        private Map<String, Object> generatedKeys = Map.of("id", 10);
        private boolean includeGeneratedKeysInRow;

        @Override
        public InsertResult insertSafe(String table, Map<String, Object> row, List<String> ignoreKeys) {
            this.table = table;
            this.row = row;
            this.ignoreKeys = ignoreKeys;
            if (!includeGeneratedKeysInRow) {
                return new InsertResult(1, generatedKeys, row);
            }
            Map<String, Object> rowWithKeys = new java.util.LinkedHashMap<>(row);
            rowWithKeys.putAll(generatedKeys);
            return new InsertResult(1, generatedKeys, rowWithKeys);
        }

        @Override
        public int execute(String sql, List<Object> params) {
            return 0;
        }

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> params) {
            return List.of();
        }

        @Override
        public void truncateTable(String table) {
        }

        @Override
        public void close() {
        }
    }
}
