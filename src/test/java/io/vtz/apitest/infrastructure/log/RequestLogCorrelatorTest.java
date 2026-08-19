package io.vtz.apitest.infrastructure.log;

import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.log.LogLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLogCorrelatorTest {
    @Test
    void correlatesRequestLogsByFailureUrl() {
        RequestLogCorrelator correlator = new RequestLogCorrelator();
        correlator.accept(event("token claims", "abc", "/api/v1/test?id=1", null));
        correlator.accept(event("REQUEST", "abc", "/api/v1/test?id=1", 500));

        var result = correlator.findByFailureUrl("http://localhost:1323/api/v1/test?id=1");

        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());
    }

    @Test
    void returnsImmutableSnapshotForFailureUrlLogs() {
        RequestLogCorrelator correlator = new RequestLogCorrelator();
        correlator.accept(event("REQUEST", "abc", "/api/v1/test?id=1", 500));

        var result = correlator.findByFailureUrl("http://localhost:1323/api/v1/test?id=1").orElseThrow();

        correlator.accept(event("later", "abc", "/api/v1/test?id=1", null));

        assertEquals(1, result.size());
        assertThrows(UnsupportedOperationException.class, result::clear);
        assertEquals(2, correlator.findByFailureUrl("http://localhost:1323/api/v1/test?id=1").orElseThrow().size());
    }

    private static LogEvent event(String message, String requestId, String uri, Integer status) {
        return new LogEvent(
                Instant.now(),
                LogLevel.INFO,
                message,
                message,
                true,
                requestId,
                uri,
                "GET",
                status,
                null,
                null,
                null);
    }
}
