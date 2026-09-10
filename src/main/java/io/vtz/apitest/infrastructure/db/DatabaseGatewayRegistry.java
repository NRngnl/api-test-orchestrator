package io.vtz.apitest.infrastructure.db;

import io.vtz.apitest.application.port.DatabasePort;
import io.vtz.apitest.domain.db.DatabaseTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Karate re-evaluates karate-config.js for every scenario, so the documented thin-config pattern
// constructs a new ApiTestOrchestrator per scenario. A pool per construction is never closed and
// grows with the suite until MySQL answers "Too many connections", so pools are shared per target
// for the life of the process instead (the same shape as KarateMockServerRegistry).
public class DatabaseGatewayRegistry {
    private final Function<DatabaseTarget, DatabasePort> factory;
    private final Map<DatabaseTarget, DatabasePort> gateways = new LinkedHashMap<>();   // guarded by `this`

    public DatabaseGatewayRegistry() {
        this(JdbcDatabaseGateway::new);
    }

    DatabaseGatewayRegistry(Function<DatabaseTarget, DatabasePort> factory) {
        this.factory = factory;
    }

    public synchronized DatabasePort acquire(DatabaseTarget target) {
        return gateways.computeIfAbsent(target, factory);
    }

    public void closeAll() {
        List<DatabasePort> toClose;
        synchronized (this) {
            toClose = new ArrayList<>(gateways.values());
            gateways.clear();
        }
        toClose.forEach(DatabasePort::close);
    }
}
