package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.service.ChecksumService;
import io.vtz.apitest.application.service.DateFactory;
import io.vtz.apitest.application.service.IdentifierFactory;
import io.vtz.apitest.domain.mock.RunningMockServer;
import io.vtz.apitest.domain.process.CommandResult;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeatureBridge {
    private final Map<String, DatabaseFacade> databases;
    private final String defaultDatabaseName;
    private final MockServerFacade mockServers;
    private final DateFactory dates;
    private final IdentifierFactory identifiers;
    private final ChecksumService checksums;
    private final ProcessFacade process;

    public FeatureBridge(
            DatabaseFacade database,
            MockServerFacade mockServers,
            DateFactory dates,
            IdentifierFactory identifiers,
            ChecksumService checksums,
            ProcessFacade process) {
        this(database == null ? Map.of() : Map.of("default", database), "default", mockServers, dates, identifiers, checksums, process);
    }

    public FeatureBridge(
            Map<String, DatabaseFacade> databases,
            String defaultDatabaseName,
            MockServerFacade mockServers,
            DateFactory dates,
            IdentifierFactory identifiers,
            ChecksumService checksums,
            ProcessFacade process) {
        this.databases = Collections.unmodifiableMap(databases == null ? Map.of() : new LinkedHashMap<>(databases));
        this.defaultDatabaseName = defaultDatabaseName == null || defaultDatabaseName.isBlank() ? "default" : defaultDatabaseName;
        this.mockServers = mockServers;
        this.dates = dates;
        this.identifiers = identifiers;
        this.checksums = checksums;
        this.process = process;
    }

    public DatabaseFacade db(String name) {
        return database(name);
    }

    public Set<String> dbNames() {
        return databases.keySet();
    }

    public List<Map<String, Object>> dbQuery(String sql) {
        return database().query(sql);
    }

    public List<Map<String, Object>> dbQuery(String databaseName, String sql) {
        return database(databaseName).query(sql);
    }

    public int dbUpdate(String sql) {
        return database().execute(sql);
    }

    public int dbUpdate(String databaseName, String sql) {
        return database(databaseName).execute(sql);
    }

    public Map<String, Object> insertSafe(String table, Map<String, Object> row) {
        return database().insertSafe(table, row);
    }

    public Map<String, Object> insertSafe(String table, Map<String, Object> row, List<String> ignoreKeys) {
        return database().insertSafe(table, row, ignoreKeys);
    }

    public Map<String, Object> insertSafe(String databaseName, String table, Map<String, Object> row) {
        return database(databaseName).insertSafe(table, row);
    }

    public Map<String, Object> insertSafe(String databaseName, String table, Map<String, Object> row, List<String> ignoreKeys) {
        return database(databaseName).insertSafe(table, row, ignoreKeys);
    }

    public void truncateTable(String table) {
        database().truncateTable(table);
    }

    public void truncateTable(String databaseName, String table) {
        database(databaseName).truncateTable(table);
    }

    public Date sqlDate(int year, int month, int day) {
        return dates.sqlDate(year, month, day);
    }

    public Date sqlDateFromString(String value) {
        return dates.sqlDateFromString(value);
    }

    public Timestamp sqlTimestamp(int year, int month, int day, int hour, int minute, int second) {
        return dates.sqlTimestamp(year, month, day, hour, minute, second);
    }

    public Timestamp sqlTimestampFromString(String value) {
        return dates.sqlTimestampFromString(value);
    }

    public Timestamp sqlTimestampFromDate(java.util.Date date, int years, int months, int days, int hours, int minutes, int seconds) {
        java.util.Date base = date == null ? new java.util.Date() : date;
        return sqlTimestampAdd(new Timestamp(base.getTime()), years, months, days, hours, minutes, seconds);
    }

    public Timestamp sqlTimestampAdd(
            Timestamp timestamp,
            int years,
            int months,
            int days,
            int hours,
            int minutes,
            int seconds) {
        return dates.sqlTimestampAdd(timestamp, years, months, days, hours, minutes, seconds);
    }

    public String dateToString(Object date, String pattern) {
        return dates.format(date, pattern);
    }

    public String generateRandomString() {
        return identifiers.randomString();
    }

    public String generateRandomString(int length) {
        return identifiers.randomString(length);
    }

    public String md5(String value) {
        return checksums.md5(value);
    }

    public boolean isBefore(Object left, Object right) {
        return compareTemporal(left, right) < 0;
    }

    public boolean isAfter(Object left, Object right) {
        return compareTemporal(left, right) > 0;
    }

    public RunningMockServer startMockServer(Map<String, Object> options) {
        return mockServers.start(options);
    }

    public void stopMockServer(int port) {
        mockServers.runningServers().stream()
                .filter(server -> server.port() == port)
                .map(RunningMockServer::id)
                .toList()
                .forEach(mockServers::stop);
    }

    public void stopAllMockServers() {
        mockServers.stopAll();
    }

    public CommandResult execCmd(Map<String, Object> options) {
        return process.execCmd(options);
    }

    private DatabaseFacade database() {
        DatabaseFacade database = databases.get(defaultDatabaseName);
        if (database == null && !databases.isEmpty()) {
            return databases.values().iterator().next();
        }
        if (database == null) {
            throw new IllegalStateException("Database is not configured");
        }
        return database;
    }

    private DatabaseFacade database(String name) {
        DatabaseFacade database = databases.get(name);
        if (database == null) {
            throw new IllegalArgumentException("Database is not configured: " + name);
        }
        return database;
    }

    private static int compareTemporal(Object left, Object right) {
        if (left instanceof java.util.Date leftDate && right instanceof java.util.Date rightDate) {
            return Long.compare(leftDate.getTime(), rightDate.getTime());
        }
        return comparableText(left).compareTo(comparableText(right));
    }

    private static String comparableText(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot compare null temporal value");
        }
        return value.toString();
    }
}
