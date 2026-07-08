package io.vtz.apitest.interfaces.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.log.LogType;
import io.vtz.apitest.infrastructure.config.FrameworkConfig;

import java.util.Locale;
import java.util.Map;

/**
 * Colors API JSON log lines for the console.
 *
 * <p>Each line is tinted with the configured per-level base color; on top of that, key/pattern
 * values are highlighted: the SQL query in bright yellow, {@code rows_affected} in bright green,
 * and the HTTP {@code status} in red ({@code >= 400}) or green. This mirrors a reference
 * log-classification/coloring implementation.
 *
 * <p><b>Leak safety.</b> Untrusted log content is stripped of control characters before output,
 * so an escape sequence embedded in the log text cannot inject color/formatting into the
 * operator's terminal. Every highlight we add is self-contained ({@code color + token + RESET +
 * base}) and each colored line ends with {@code RESET}, so our own colors cannot bleed past a
 * token or past the end of the line.
 *
 * <p><b>Type coverage.</b> Only {@code API_SQL} and {@code API_REQUEST} carry intra-line key
 * highlights. {@code API_ERROR}, {@code API_BODY_DUMP} and {@code API_GENERAL} intentionally
 * render with the configurable per-level base tint only (error logs already map to red via the
 * default level palette); this keeps colors configurable instead of hard-coded per type.
 */
final class ApiLogFormatter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Control Sequence Introducer: ESC + '[' built from the char code to avoid raw ESC bytes in source. */
    private static final String CSI = ((char) 27) + "[";
    private static final String RESET = CSI + "0m";
    private static final String BOLD = CSI + "1m";
    private static final String SQL_HIGHLIGHT = CSI + "93m";                 // bright yellow
    private static final String ROWS_HIGHLIGHT = CSI + "92m" + BOLD;         // bright green, bold
    private static final String STATUS_OK_HIGHLIGHT = CSI + "32m";           // green
    private static final String STATUS_ERROR_HIGHLIGHT = CSI + "31m" + BOLD; // red, bold
    private static final int STATUS_ERROR_THRESHOLD = 400;

    private static final Map<String, String> ANSI_COLORS = Map.ofEntries(
            Map.entry("BLACK", CSI + "30m"),
            Map.entry("RED", CSI + "31m"),
            Map.entry("GREEN", CSI + "32m"),
            Map.entry("YELLOW", CSI + "33m"),
            Map.entry("BLUE", CSI + "34m"),
            Map.entry("MAGENTA", CSI + "35m"),
            Map.entry("CYAN", CSI + "36m"),
            Map.entry("WHITE", CSI + "37m"),
            Map.entry("BRIGHT_BLACK", CSI + "90m"),
            Map.entry("BRIGHT_RED", CSI + "91m"),
            Map.entry("BRIGHT_GREEN", CSI + "92m"),
            Map.entry("BRIGHT_YELLOW", CSI + "93m"),
            Map.entry("BRIGHT_BLUE", CSI + "94m"),
            Map.entry("BRIGHT_MAGENTA", CSI + "95m"),
            Map.entry("BRIGHT_CYAN", CSI + "96m"),
            Map.entry("BRIGHT_WHITE", CSI + "97m"));

    private final FrameworkConfig.Logging logging;

    ApiLogFormatter(FrameworkConfig.Logging logging) {
        this.logging = logging == null ? new FrameworkConfig.Logging() : logging;
    }

    String apiLine(LogEvent event) {
        return format("[api] ", event);
    }

    String indentedApiLine(LogEvent event) {
        return format("  ", event);
    }

    private String format(String prefix, LogEvent event) {
        String body = sanitize(event == null || event.raw() == null ? "" : event.raw());
        String base = baseColor(event);
        if (base == null) {
            return prefix + body;
        }
        String highlighted = highlight(body, LogType.classify(event), event, base);
        return base + prefix + highlighted + RESET;
    }

    /**
     * Removes C0 control characters (except tab) from untrusted log content so that an escape
     * sequence embedded in the log text cannot inject color/formatting into the operator's
     * terminal. Applied before any of our own control codes are added.
     */
    private static String sanitize(String text) {
        StringBuilder cleaned = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean control = c < 0x20 && c != '\t';
            if (control && cleaned == null) {
                cleaned = new StringBuilder(text.length()).append(text, 0, i);
            } else if (cleaned != null && !control) {
                cleaned.append(c);
            }
        }
        return cleaned == null ? text : cleaned.toString();
    }

    private String baseColor(LogEvent event) {
        if (!logging.colors || event == null || !event.structured() || logging.jsonLogColors == null) {
            return null;
        }
        String configured = logging.jsonLogColors.get(event.level().name());
        if (configured == null) {
            configured = logging.jsonLogColors.get(event.level().name().toLowerCase(Locale.ROOT));
        }
        return toAnsiColor(configured);
    }

    private static String highlight(String body, LogType type, LogEvent event, String base) {
        return switch (type) {
            case API_SQL -> highlightRows(highlightSql(body, event, base), event, base);
            case API_REQUEST -> highlightStatus(body, event, base);
            default -> body;
        };
    }

    /**
     * Highlights the SQL query value. The token is anchored to the {@code "sql":} key and matched
     * against the JSON-encoded form of the value, so a value that also appears elsewhere (e.g. in
     * {@code msg}) is not mistakenly highlighted and escaped/multi-line SQL still matches the raw
     * wire text.
     */
    private static String highlightSql(String body, LogEvent event, String base) {
        String sql = event.sql();
        if (sql == null || sql.isBlank()) {
            return body;
        }
        String encoded;
        try {
            encoded = MAPPER.writeValueAsString(sql); // JSON string literal incl. surrounding quotes + escapes
        } catch (JsonProcessingException e) {
            return body;
        }
        String keyToken = "\"sql\":";
        int keyIndex = body.indexOf(keyToken + encoded);
        if (keyIndex < 0) {
            return body;
        }
        int valueStart = keyIndex + keyToken.length() + 1; // skip the opening quote
        int valueLength = encoded.length() - 2;             // drop the surrounding quotes
        return wrap(body, valueStart, valueLength, SQL_HIGHLIGHT, base);
    }

    private static String highlightRows(String body, LogEvent event, String base) {
        Long rows = event.rowsAffected();
        if (rows == null) {
            return body;
        }
        return highlightNumber(body, "rows_affected", Long.toString(rows), ROWS_HIGHLIGHT, base);
    }

    private static String highlightStatus(String body, LogEvent event, String base) {
        Integer status = event.status();
        if (status == null) {
            return body;
        }
        String color = status >= STATUS_ERROR_THRESHOLD ? STATUS_ERROR_HIGHLIGHT : STATUS_OK_HIGHLIGHT;
        return highlightNumber(body, "status", Integer.toString(status), color, base);
    }

    /**
     * Highlights a numeric field value located by its {@code "key":} anchor, tolerating optional
     * whitespace after the colon. Returns {@code body} unchanged when the field or the exact value
     * is not found.
     */
    private static String highlightNumber(String body, String key, String value, String color, String base) {
        String keyToken = "\"" + key + "\":";
        int keyIndex = body.indexOf(keyToken);
        if (keyIndex < 0) {
            return body;
        }
        int valueStart = keyIndex + keyToken.length();
        while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart))) {
            valueStart++;
        }
        if (!body.startsWith(value, valueStart)) {
            return body;
        }
        return wrap(body, valueStart, value.length(), color, base);
    }

    /**
     * Wraps {@code body[start, start+length)} in {@code color}, then restores {@code base} so the
     * highlight color cannot leak past the value and the rest of the line keeps its base tint.
     */
    private static String wrap(String body, int start, int length, String color, String base) {
        int end = start + length;
        return body.substring(0, start)
                + color + body.substring(start, end) + RESET + base
                + body.substring(end);
    }

    private static String toAnsiColor(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String key = configured.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return ANSI_COLORS.get(key);
    }
}
