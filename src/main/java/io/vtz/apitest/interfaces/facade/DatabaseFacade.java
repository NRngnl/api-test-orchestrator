package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.port.DatabasePort;
import io.vtz.apitest.application.service.FixtureRowPreparer;
import io.vtz.apitest.application.service.IdentifierFactory;
import io.vtz.apitest.domain.db.FixturePolicy;
import io.vtz.apitest.domain.db.InsertResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseFacade implements AutoCloseable {
    private final DatabasePort database;
    private final FixtureRowPreparer fixtureRowPreparer;

    public DatabaseFacade(DatabasePort database) {
        this(database, new FixtureRowPreparer(FixturePolicy.none(), new IdentifierFactory()::randomString));
    }

    public DatabaseFacade(DatabasePort database, FixtureRowPreparer fixtureRowPreparer) {
        this.database = database;
        this.fixtureRowPreparer = fixtureRowPreparer;
    }

    public Map<String, Object> insertSafe(String table, Map<String, Object> row) {
        return insertSafe(table, row, List.of());
    }

    public Map<String, Object> insertSafe(String table, Map<String, Object> row, List<String> ignoreKeys) {
        Map<String, Object> prepared = fixtureRowPreparer.prepare(table, row, ignoreKeys);
        return rowWithGeneratedKeys(database.insertSafe(table, prepared, List.of()));
    }

    public int execute(String sql) {
        return database.execute(sql, List.of());
    }

    public int execute(String sql, List<Object> params) {
        return database.execute(sql, params);
    }

    public List<Map<String, Object>> query(String sql) {
        return database.query(sql, List.of());
    }

    public List<Map<String, Object>> query(String sql, List<Object> params) {
        return database.query(sql, params);
    }

    public void truncateTable(String table) {
        database.truncateTable(table);
    }

    @Override
    public void close() {
        database.close();
    }

    private static Map<String, Object> rowWithGeneratedKeys(InsertResult result) {
        Map<String, Object> values = new LinkedHashMap<>(result.row());
        Map<String, Object> generatedKeys = result.generatedKeys();
        generatedKeys.keySet().stream()
                .filter(key -> !"id".equals(key))
                .forEach(values::remove);
        if (generatedKeys.containsKey("id")) {
            values.put("id", generatedKeys.get("id"));
        } else if (!values.containsKey("id") && !generatedKeys.isEmpty()) {
            values.put("id", generatedKeys.values().iterator().next());
        }
        return values;
    }
}
