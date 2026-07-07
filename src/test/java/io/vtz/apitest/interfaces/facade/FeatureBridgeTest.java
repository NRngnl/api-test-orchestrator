package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.port.DatabasePort;
import io.vtz.apitest.application.port.MockServerPort;
import io.vtz.apitest.application.service.ChecksumService;
import io.vtz.apitest.application.service.DateFactory;
import io.vtz.apitest.application.service.IdentifierFactory;
import io.vtz.apitest.domain.db.InsertResult;
import io.vtz.apitest.domain.mock.MockServerSpec;
import io.vtz.apitest.domain.mock.RunningMockServer;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureBridgeTest {
    @Test
    void exposesCommonFeatureHelpersWithoutJavaScriptInteropDetails() {
        RecordingDatabasePort databasePort = new RecordingDatabasePort(3, 10);
        RecordingDatabasePort auditDatabasePort = new RecordingDatabasePort(5, 20);
        RecordingMockServerPort mockPort = new RecordingMockServerPort();
        mockPort.runningServers = List.of(new RunningMockServer(
                "existing",
                "classpath:old.feature",
                39992,
                false,
                "http://localhost:39992"));
        Map<String, DatabaseFacade> databases = new LinkedHashMap<>();
        databases.put("primary", new DatabaseFacade(databasePort));
        databases.put("audit", new DatabaseFacade(auditDatabasePort));
        FeatureBridge bridge = new FeatureBridge(
                databases,
                "primary",
                new MockServerFacade(mockPort),
                new DateFactory(),
                new IdentifierFactory(),
                new ChecksumService());

        assertEquals(2, bridge.dbQuery("SELECT 1").size());
        assertEquals(3, bridge.dbUpdate("UPDATE cases SET status = 'CLOSED'"));
        assertEquals(10, bridge.insertSafe("cases", Map.of("status", "OPEN")).get("id"));
        assertEquals(5, bridge.dbUpdate("audit", "UPDATE events SET handled = 1"));
        assertEquals(20, bridge.insertSafe("audit", "events", Map.of("source", "feature")).get("id"));
        assertEquals("2026-07-07", bridge.dateToString(bridge.sqlDate(2026, 7, 7), "yyyy-MM-dd"));
        assertEquals(
                Timestamp.valueOf("2026-07-08 01:02:03"),
                bridge.sqlTimestampAdd(bridge.sqlTimestamp(2026, 7, 7, 1, 2, 3), 0, 0, 1, 0, 0, 0));
        assertEquals(12, bridge.generateRandomString(12).length());
        assertEquals("098f6bcd4621d373cade4e832627b4f6", bridge.md5("test"));
        assertTrue(bridge.isBefore("2025-10-01", "2025-10-31"));
        assertFalse(bridge.isBefore("2025-10-31", "2025-10-01"));
        assertTrue(bridge.isAfter(
                Timestamp.valueOf("2026-07-07 01:02:04"),
                Timestamp.valueOf("2026-07-07 01:02:03")));

        bridge.stopMockServer(39992);
        RunningMockServer replacement = bridge.startMockServer(Map.of("feature", "classpath:new.feature", "port", 39992));

        assertEquals("id", replacement.id());
        assertEquals(List.of("stop:existing", "start:39992"), mockPort.events);
    }

    private static class RecordingDatabasePort implements DatabasePort {
        private final int affectedRows;
        private final int generatedId;

        private RecordingDatabasePort(int affectedRows, int generatedId) {
            this.affectedRows = affectedRows;
            this.generatedId = generatedId;
        }

        @Override
        public InsertResult insertSafe(String table, Map<String, Object> row, List<String> ignoreKeys) {
            return new InsertResult(1, Map.of("id", generatedId), row);
        }

        @Override
        public int execute(String sql, List<Object> params) {
            return affectedRows;
        }

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> params) {
            return List.of(Map.of("value", 1), Map.of("value", 2));
        }

        @Override
        public void truncateTable(String table) {
        }

        @Override
        public void close() {
        }
    }

    private static class RecordingMockServerPort implements MockServerPort {
        private List<RunningMockServer> runningServers = List.of();
        private final List<String> events = new ArrayList<>();

        @Override
        public RunningMockServer start(MockServerSpec spec) {
            events.add("start:" + spec.port());
            return new RunningMockServer("id", spec.feature(), spec.port(), spec.ssl(), "http://localhost:" + spec.port());
        }

        @Override
        public void stop(String id) {
            events.add("stop:" + id);
            runningServers = runningServers.stream()
                    .filter(server -> !server.id().equals(id))
                    .toList();
        }

        @Override
        public void stopAll() {
        }

        @Override
        public List<RunningMockServer> runningServers() {
            return runningServers;
        }
    }
}
