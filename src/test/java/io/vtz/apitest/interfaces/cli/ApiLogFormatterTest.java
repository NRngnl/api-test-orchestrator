package io.vtz.apitest.interfaces.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.log.LogLevel;
import io.vtz.apitest.infrastructure.config.FrameworkConfig;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiLogFormatterTest {
    private static final String JSON = "{\"time\":\"2026-07-08T00:00:00Z\",\"level\":\"ERROR\",\"msg\":\"boom\"}";

    @Test
    void colorsJsonApiLogWithDefaultLevelColor() throws Exception {
        LogEvent event = jsonEvent();

        assertEquals(
                "\u001B[31m[api] " + JSON + "\u001B[0m",
                new ApiLogFormatter(new FrameworkConfig.Logging()).apiLine(event));
    }

    @Test
    void colorsJsonApiLogByConfiguredLevelColor() throws Exception {
        FrameworkConfig.Logging logging = new FrameworkConfig.Logging();
        logging.colors = true;
        logging.jsonLogColors = new LinkedHashMap<>();
        logging.jsonLogColors.put("ERROR", "magenta");

        LogEvent event = jsonEvent();

        assertEquals(
                "\u001B[35m[api] " + JSON + "\u001B[0m",
                new ApiLogFormatter(logging).apiLine(event));
    }

    @Test
    void leavesUnstructuredLogsUncolored() {
        FrameworkConfig.Logging logging = new FrameworkConfig.Logging();
        logging.colors = true;
        logging.jsonLogColors = new LinkedHashMap<>();
        logging.jsonLogColors.put("ERROR", "red");
        LogEvent event = new LogEvent(
                Instant.parse("2026-07-08T00:00:00Z"),
                LogLevel.ERROR,
                "not-json",
                "not-json",
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertEquals("[api] not-json", new ApiLogFormatter(logging).apiLine(event));
    }

    @Test
    void disabledColorsLeaveJsonLogUncolored() throws Exception {
        FrameworkConfig.Logging logging = new FrameworkConfig.Logging();
        logging.colors = false;
        logging.jsonLogColors = new LinkedHashMap<>();
        logging.jsonLogColors.put("ERROR", "red");

        assertEquals("[api] " + JSON, new ApiLogFormatter(logging).apiLine(jsonEvent()));
    }

    @Test
    void unknownConfiguredColorLeavesJsonLogUncolored() throws Exception {
        FrameworkConfig.Logging logging = new FrameworkConfig.Logging();
        logging.colors = true;
        logging.jsonLogColors = new LinkedHashMap<>();
        logging.jsonLogColors.put("ERROR", "brand-red");

        assertEquals("[api] " + JSON, new ApiLogFormatter(logging).apiLine(jsonEvent()));
    }

    private static LogEvent jsonEvent() throws Exception {
        return new LogEvent(
                Instant.parse("2026-07-08T00:00:00Z"),
                LogLevel.ERROR,
                "boom",
                JSON,
                new ObjectMapper().readTree(JSON),
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
