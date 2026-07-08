package io.vtz.apitest.domain.log;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogTypeTest {
    @Test
    void classifiesSqlByMessageKeyword() {
        assertEquals(LogType.API_SQL, LogType.classify(event(LogLevel.INFO, "executing SQL", null, null, null)));
    }

    @Test
    void classifiesSqlByField() {
        assertEquals(LogType.API_SQL, LogType.classify(event(LogLevel.INFO, "query", "SELECT 1", null, null)));
    }

    @Test
    void classifiesErroredSqlAsError() {
        assertEquals(LogType.API_ERROR, LogType.classify(event(LogLevel.ERROR, "SQL", "SELECT 1", null, "boom")));
    }

    @Test
    void classifiesSqlWithErrFieldAsError() {
        assertEquals(LogType.API_ERROR, LogType.classify(event(LogLevel.INFO, "SQL", "SELECT 1", null, "boom")));
    }

    @Test
    void classifiesBodyDump() {
        assertEquals(
                LogType.API_BODY_DUMP,
                LogType.classify(event(LogLevel.INFO, "request / response body dump", null, null, null)));
    }

    @Test
    void classifiesRequest() {
        assertEquals(LogType.API_REQUEST, LogType.classify(event(LogLevel.INFO, "REQUEST", null, 200, null)));
    }

    @Test
    void classifiesPlainErrorLevelAsError() {
        assertEquals(LogType.API_ERROR, LogType.classify(event(LogLevel.ERROR, "boom", null, null, null)));
    }

    @Test
    void classifiesEverythingElseAsGeneral() {
        assertEquals(LogType.API_GENERAL, LogType.classify(event(LogLevel.INFO, "hello", null, null, null)));
    }

    @Test
    void nullEventIsGeneral() {
        assertEquals(LogType.API_GENERAL, LogType.classify(null));
    }

    @Test
    void classifiesWarnWithoutMarkersAsGeneral() {
        assertEquals(LogType.API_GENERAL, LogType.classify(event(LogLevel.WARN, "slow query detected", null, null, null)));
    }

    @Test
    void classifiesDebugWithoutMarkersAsGeneral() {
        assertEquals(LogType.API_GENERAL, LogType.classify(event(LogLevel.DEBUG, "cache miss", null, null, null)));
    }

    @Test
    void blankErrorDoesNotMakeSqlAnError() {
        assertEquals(LogType.API_SQL, LogType.classify(event(LogLevel.INFO, "SQL", "SELECT 1", null, "   ")));
    }

    @Test
    void nullMessageWithErrorLevelIsError() {
        assertEquals(LogType.API_ERROR, LogType.classify(event(LogLevel.ERROR, null, null, null, null)));
    }

    private static LogEvent event(LogLevel level, String message, String sql, Integer status, String error) {
        return new LogEvent(
                Instant.parse("2026-07-08T00:00:00Z"),
                level,
                message,
                "{}",
                null,
                null,
                null,
                null,
                status,
                sql,
                error);
    }
}
