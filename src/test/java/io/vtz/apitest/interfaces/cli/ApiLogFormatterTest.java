package io.vtz.apitest.interfaces.cli;

import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.log.LogLevel;
import io.vtz.apitest.infrastructure.config.FrameworkConfig;
import io.vtz.apitest.infrastructure.log.JsonLogParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior-focused tests for {@link ApiLogFormatter}. Assertions target the coloring contract
 * (base tint wraps the line, a highlighted value is closed by RESET and the base resumes, nothing
 * leaks past the line) rather than specific palette numbers — except for status, where the red vs
 * green distinction IS the contract. Escape codes come from the single {@link #sgr} builder.
 */
class ApiLogFormatterTest {
    private static final char ESC = 27;
    private static final String RESET = sgr("0");
    private static final String STATUS_ERROR = sgr("31", "1"); // red, bold — status >= 400
    private static final String STATUS_OK = sgr("32");         // green    — status <  400
    private static final String ERROR_JSON = "{\"level\":\"ERROR\",\"msg\":\"boom\"}";

    // --- helpers -----------------------------------------------------------------------------

    private static String sgr(String... codes) {
        StringBuilder out = new StringBuilder();
        for (String code : codes) {
            out.append(ESC).append('[').append(code).append('m');
        }
        return out.toString();
    }

    private static String apiLine(String json) {
        return new ApiLogFormatter(new FrameworkConfig.Logging()).apiLine(new JsonLogParser().parse(json));
    }

    private static FrameworkConfig.Logging logging(boolean colors, String level, String color) {
        FrameworkConfig.Logging logging = new FrameworkConfig.Logging();
        logging.colors = colors;
        logging.jsonLogColors = new LinkedHashMap<>();
        logging.jsonLogColors.put(level, color);
        return logging;
    }

    /** The leading run of SGR sequences on a colored line — its base tint. */
    private static String baseOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ESC) {
            int m = line.indexOf('m', i);
            if (m < 0) {
                break;
            }
            i = m + 1;
        }
        return line.substring(0, i);
    }

    private static int escapeCount(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ESC) {
                count++;
            }
        }
        return count;
    }

    /**
     * Asserts {@code token} appears wrapped in a color (immediately preceded by an SGR sequence),
     * is closed by RESET, and the base tint resumes right after — proving the highlight color does
     * not leak past the value.
     */
    private static void assertHighlighted(String line, String token) {
        int i = line.indexOf(token);
        assertTrue(i > 0, () -> "token not found: <" + token + "> in " + line);
        assertEquals('m', line.charAt(i - 1), () -> "token not opened by a color: " + line);
        String after = line.substring(i + token.length());
        assertTrue(after.startsWith(RESET + baseOf(line)),
                () -> "token not closed by RESET+base (leak): " + line);
    }

    // --- base coloring -----------------------------------------------------------------------

    @Test
    void wrapsWholeLineInConfigurableBaseWithTrailingReset() {
        String line = apiLine(ERROR_JSON);
        // Whole line = base + prefix + body + RESET, with no interior escapes for a non-highlight line.
        assertEquals(baseOf(line) + "[api] " + ERROR_JSON + RESET, line);
    }

    @Test
    void baseColorFollowsConfiguration() {
        LogEvent event = new JsonLogParser().parse(ERROR_JSON);
        String red = new ApiLogFormatter(logging(true, "ERROR", "red")).apiLine(event);
        String magenta = new ApiLogFormatter(logging(true, "ERROR", "magenta")).apiLine(event);

        assertNotEquals(baseOf(red), baseOf(magenta), "configured color should change the base tint");
        // Everything after the base (prefix + body + reset) is identical regardless of color.
        assertEquals(red.substring(baseOf(red).length()), magenta.substring(baseOf(magenta).length()));
    }

    @Test
    void unknownConfiguredColorLeavesLineUncolored() {
        String line = new ApiLogFormatter(logging(true, "ERROR", "brand-red"))
                .apiLine(new JsonLogParser().parse(ERROR_JSON));
        assertEquals("[api] " + ERROR_JSON, line);
        assertEquals(0, escapeCount(line));
    }

    @Test
    void disabledColorsLeaveLineUncolored() {
        String line = new ApiLogFormatter(logging(false, "ERROR", "red"))
                .apiLine(new JsonLogParser().parse(ERROR_JSON));
        assertEquals("[api] " + ERROR_JSON, line);
        assertEquals(0, escapeCount(line));
    }

    @Test
    void unstructuredLogsAreLeftUncolored() {
        LogEvent event = new LogEvent(
                Instant.parse("2026-07-08T00:00:00Z"), LogLevel.ERROR, "not-json", "not-json",
                false, null, null, null, null, null, null, null);
        assertEquals("[api] not-json", new ApiLogFormatter(logging(true, "ERROR", "red")).apiLine(event));
    }

    // --- key / pattern highlighting ----------------------------------------------------------

    @Test
    void highlightsSqlQueryValue() {
        String line = apiLine("{\"level\":\"INFO\",\"msg\":\"SQL exec\",\"sql\":\"SELECT 1\"}");
        assertHighlighted(line, "SELECT 1");
        assertTrue(line.endsWith(RESET), line);
    }

    @Test
    void highlightsSqlValueContainingJsonEscapes() {
        // Regression: a decoded sql value with quotes must still match the escaped raw wire form.
        String line = apiLine("{\"level\":\"INFO\",\"msg\":\"SQL\",\"sql\":\"SELECT \\\"x\\\"\"}");
        assertHighlighted(line, "SELECT \\\"x\\\""); // escaped form as it appears in the raw line
    }

    @Test
    void highlightsSqlFieldValueNotItsOccurrenceInMessage() {
        // The same text appears in msg and in the sql field; only the anchored sql field is highlighted.
        String line = apiLine("{\"level\":\"INFO\",\"msg\":\"ran SELECT 1 now\",\"sql\":\"SELECT 1\"}");
        assertTrue(line.contains("\"msg\":\"ran SELECT 1 now\""), "msg text must stay plain: " + line);
        assertTrue(line.contains("\"sql\":\"" + sgr("93") + "SELECT 1" + RESET), "sql field must be highlighted: " + line);
    }

    @Test
    void highlightsRowsAffected() {
        String line = apiLine("{\"level\":\"INFO\",\"msg\":\"SQL\",\"sql\":\"SELECT 1\",\"rows_affected\":42}");
        assertHighlighted(line, "42");
    }

    @Test
    void highlightsStatusByThreshold() {
        // Boundary coverage for `status >= 400`: 199/399 -> green, 400/500 -> red.
        assertStatusColor(199, STATUS_OK);
        assertStatusColor(399, STATUS_OK);
        assertStatusColor(400, STATUS_ERROR);
        assertStatusColor(500, STATUS_ERROR);
    }

    private static void assertStatusColor(int status, String expectedColor) {
        String line = apiLine("{\"level\":\"INFO\",\"msg\":\"REQUEST\",\"status\":" + status + "}");
        assertTrue(line.contains(expectedColor + status + RESET),
                () -> "status " + status + " wrong color in " + line);
        assertTrue(line.endsWith(RESET), line);
    }

    @Test
    void toleratesWhitespaceAfterColonInStatus() {
        String line = apiLine("{\"level\":\"INFO\",\"msg\":\"REQUEST\",\"status\": 404}");
        assertTrue(line.contains(STATUS_ERROR + "404" + RESET), line);
    }

    // --- indentation, null, and highlight gating ---------------------------------------------

    @Test
    void indentedApiLineUsesTwoSpacePrefix() {
        LogEvent event = new JsonLogParser().parse(ERROR_JSON);
        String line = new ApiLogFormatter(new FrameworkConfig.Logging()).indentedApiLine(event);
        assertTrue(line.startsWith(baseOf(line) + "  "), line);
        assertTrue(line.endsWith(RESET), line);
    }

    @Test
    void nullEventProducesPrefixOnly() {
        ApiLogFormatter formatter = new ApiLogFormatter(new FrameworkConfig.Logging());
        assertEquals("[api] ", formatter.apiLine(null));
        assertEquals("  ", formatter.indentedApiLine(null));
    }

    @Test
    void highlightsAreSuppressedWhenColorsDisabled() {
        // A highlightable REQUEST/status line with colors off must emit no escapes at all.
        String json = "{\"level\":\"INFO\",\"msg\":\"REQUEST\",\"status\":500}";
        String line = new ApiLogFormatter(logging(false, "INFO", "green"))
                .apiLine(new JsonLogParser().parse(json));
        assertEquals("[api] " + json, line);
        assertEquals(0, escapeCount(line));
    }

    // --- leak safety: escape sequences in untrusted log content ------------------------------

    @Test
    void doesNotInjectEscapeSequencesFromLogContent() {
        // Attacker-influenced content: a real ESC byte smuggled after the JSON object.
        String malicious = "{\"level\":\"INFO\",\"msg\":\"SQL\"}" + ESC + "[5m PWNED";
        String line = apiLine(malicious);
        assertFalse(line.contains(ESC + "[5m"), "raw escape sequence leaked into output: " + line);
        assertTrue(line.contains("[5m PWNED"), "sanitized content should remain as plain text: " + line);
    }
}
