package io.vtz.apitest.application.port;

import io.vtz.apitest.domain.db.InsertResult;

import java.util.List;
import java.util.Map;

public interface DatabasePort extends AutoCloseable {
    InsertResult insertSafe(String table, Map<String, Object> row, List<String> ignoreKeys);

    int execute(String sql, List<Object> params);

    List<Map<String, Object>> query(String sql, List<Object> params);

    void truncateTable(String table);

    @Override
    void close();
}
