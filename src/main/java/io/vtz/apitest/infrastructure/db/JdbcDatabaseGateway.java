package io.vtz.apitest.infrastructure.db;

import io.vtz.apitest.application.port.DatabasePort;
import io.vtz.apitest.domain.db.DatabaseTarget;
import io.vtz.apitest.domain.db.InsertResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class JdbcDatabaseGateway implements DatabasePort {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final HikariDataSource dataSource;

    public JdbcDatabaseGateway(DatabaseTarget target) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(target.jdbcUrl());
        config.setUsername(target.username());
        config.setPassword(target.password());
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public InsertResult insertSafe(String table, Map<String, Object> row, List<String> ignoreKeys) {
        requireIdentifier(table);
        Map<String, Object> values = new LinkedHashMap<>();
        if (row != null) {
            values.putAll(row);
        }
        if (ignoreKeys != null) {
            ignoreKeys.forEach(values::remove);
        }
        values.keySet().forEach(JdbcDatabaseGateway::requireIdentifier);

        String columns = String.join(", ", values.keySet());
        String placeholders = String.join(", ", values.keySet().stream().map(key -> "?").toList());
        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, new ArrayList<>(values.values()));
            int affected = statement.executeUpdate();
            Map<String, Object> keys = readGeneratedKeys(statement);
            return new InsertResult(affected, keys, values);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert into " + table + ": " + e.getMessage(), e);
        }
    }

    @Override
    public int execute(String sql, List<Object> params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to execute SQL: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> query(String sql, List<Object> params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return readRows(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query SQL: " + e.getMessage(), e);
        }
    }

    @Override
    public void truncateTable(String table) {
        requireIdentifier(table);
        execute("TRUNCATE TABLE " + table, List.of());
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        List<Object> safeParams = params == null ? List.of() : params;
        for (int i = 0; i < safeParams.size(); i++) {
            Object value = safeParams.get(i);
            int index = i + 1;
            if (value instanceof Date date) {
                statement.setDate(index, date);
            } else if (value instanceof Timestamp timestamp) {
                statement.setTimestamp(index, timestamp);
            } else if (value instanceof LocalDate localDate) {
                statement.setDate(index, Date.valueOf(localDate));
            } else if (value instanceof LocalDateTime localDateTime) {
                statement.setTimestamp(index, Timestamp.valueOf(localDateTime));
            } else {
                statement.setObject(index, value);
            }
        }
    }

    private static Map<String, Object> readGeneratedKeys(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.getGeneratedKeys()) {
            List<Map<String, Object>> rows = readRows(resultSet);
            return rows.isEmpty() ? Map.of() : rows.getFirst();
        }
    }

    private static List<Map<String, Object>> readRows(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columns = metaData.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private static void requireIdentifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + value);
        }
    }
}
