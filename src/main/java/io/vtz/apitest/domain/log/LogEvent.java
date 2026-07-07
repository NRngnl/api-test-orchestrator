package io.vtz.apitest.domain.log;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Optional;

public record LogEvent(
        Instant timestamp,
        LogLevel level,
        String message,
        String raw,
        JsonNode structured,
        String requestId,
        String uri,
        String method,
        Integer status,
        String sql,
        String error
) {
    public Optional<String> requestIdOptional() {
        return Optional.ofNullable(requestId).filter(value -> !value.isBlank());
    }

    public String searchableText() {
        return String.join(" ",
                nullToEmpty(message),
                nullToEmpty(uri),
                nullToEmpty(method),
                nullToEmpty(sql),
                nullToEmpty(error),
                raw == null ? "" : raw);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
