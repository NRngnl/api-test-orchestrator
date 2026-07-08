package io.vtz.apitest.infrastructure.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.log.LogLevel;

import java.time.Instant;

public class JsonLogParser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LogEvent parse(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            return new LogEvent(
                    parseInstant(text(node, "time")),
                    LogLevel.parse(text(node, "level")),
                    text(node, "msg"),
                    line,
                    true,
                    text(node, "request_id"),
                    text(node, "uri"),
                    text(node, "method"),
                    integer(node, "status"),
                    text(node, "sql"),
                    text(node, "err"),
                    longValue(node, "rows_affected"));
        } catch (Exception ignored) {
            return new LogEvent(
                    Instant.now(),
                    LogLevel.INFO,
                    line,
                    line,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.asLong();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }
}
