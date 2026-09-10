package io.vtz.apitest.infrastructure.db;

import io.vtz.apitest.application.port.DatabasePort;
import io.vtz.apitest.domain.db.DatabaseTarget;
import io.vtz.apitest.domain.db.InsertResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseGatewayRegistryTest {
    private final List<FakeDatabasePort> created = new ArrayList<>();
    private final DatabaseGatewayRegistry registry = new DatabaseGatewayRegistry(target -> {
        FakeDatabasePort port = new FakeDatabasePort();
        created.add(port);
        return port;
    });

    @Test
    void acquireReturnsOneGatewayPerTargetAcrossCallers() {
        DatabaseTarget primary = new DatabaseTarget("jdbc:mysql://primary/db", "user", "secret");
        DatabaseTarget replica = new DatabaseTarget("jdbc:mysql://replica/db", "user", "secret");

        DatabasePort first = registry.acquire(primary);
        DatabasePort second = registry.acquire(new DatabaseTarget("jdbc:mysql://primary/db", "user", "secret"));
        DatabasePort third = registry.acquire(replica);

        assertSame(first, second);
        assertNotSame(first, third);
        assertEquals(2, created.size());
    }

    @Test
    void closeAllClosesEveryGatewayAndLetsTheNextAcquireStartFresh() {
        DatabaseTarget target = new DatabaseTarget("jdbc:mysql://primary/db", "user", "secret");
        DatabasePort before = registry.acquire(target);

        registry.closeAll();
        DatabasePort after = registry.acquire(target);

        assertTrue(created.get(0).closed);
        assertNotSame(before, after);
        assertEquals(2, created.size());
    }

    private static class FakeDatabasePort implements DatabasePort {
        private boolean closed;

        @Override
        public InsertResult insertSafe(String table, Map<String, Object> row, List<String> ignoreKeys) {
            return new InsertResult(0, Map.of(), row);
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
            closed = true;
        }
    }
}
