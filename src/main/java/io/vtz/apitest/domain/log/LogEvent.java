package io.vtz.apitest.domain.log;

import java.time.Instant;
import java.util.Optional;

/**
 * A parsed API log line. Pure domain: it carries only primitive/JDK-typed fields, no parser or
 * framework types. {@code structured} records whether the raw line was successfully parsed as a
 * JSON object (as opposed to plain text); JSON parsing itself lives in infrastructure.
 */
public record LogEvent(
        Instant timestamp,
        LogLevel level,
        String message,
        String raw,
        boolean structured,
        String requestId,
        String uri,
        String method,
        Integer status,
        String sql,
        String error,
        Long rowsAffected
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
