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
 * <p>Each structured line is tinted with the configured per-log-type base color, falling back to
 * the per-level palette when no type color is configured. On top of that, key/pattern values are
 * highlighted with the configured {@code logging.jsonLogHighlightColors} palette. This mirrors a
 * reference log-classification/coloring implementation.
 *
 * <p><b>Leak safety.</b> Untrusted log content is stripped of control characters before output,
 * so an escape sequence embedded in the log text cannot inject color/formatting into the
 * operator's terminal. Every highlight we add is self-contained ({@code color + token + RESET +
 * base}) and each colored line ends with {@code RESET}, so our own colors cannot bleed past a
 * token or past the end of the line.
 *
 * <p><b>Type coverage.</b> Only {@code API_SQL} and {@code API_REQUEST} carry intra-line key
 * highlights. {@code API_ERROR}, {@code API_BODY_DUMP} and {@code API_GENERAL} render with their
 * configured base tint only; this keeps colors configurable instead of hard-coded per type.
 */
public final class ApiLogFormatter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Control Sequence Introducer: ESC + '[' built from the char code to avoid raw ESC bytes in source. */
    private static final String CSI = ((char) 27) + "[";
    private static final String RESET = CSI + "0m";
    private static final String BOLD = CSI + "1m";
    private static final String DIMMED = CSI + "2m";
    private static final String SQL_HIGHLIGHT = "sql";
    private static final String ROWS_HIGHLIGHT = "rowsAffected";
    private static final String STATUS_OK_HIGHLIGHT = "statusOk";
    private static final String STATUS_ERROR_HIGHLIGHT = "statusError";
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

    public ApiLogFormatter(FrameworkConfig.Logging logging) {
        this.logging = logging == null ? new FrameworkConfig.Logging() : logging;
    }

    String apiLine(LogEvent event) {
        return format("[api] ", event);
    }

    String indentedApiLine(LogEvent event) {
        return format("  ", event);
    }

    /**
     * Renders a log line with a caller-supplied prefix (e.g. {@code "[batch] "}), applying the same
     * structured-JSON coloring as {@link #apiLine(LogEvent)}. Lets non-API processes stream their
     * output through the identical formatter under a custom label.
     */
    public String line(String prefix, LogEvent event) {
        return format(prefix == null ? "" : prefix, event);
    }

    private String format(String prefix, LogEvent event) {
        String body = sanitize(event == null || event.raw() == null ? "" : event.raw());
        LogType type = LogType.classify(event);
        String base = baseColor(event, type);
        if (base == null) {
            return prefix + body;
        }
        String highlighted = highlight(body, type, event, base);
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

    private String baseColor(LogEvent event, LogType type) {
        if (!logging.colors || event == null || !event.structured()) {
            return null;
        }
        String configured = configuredValue(logging.jsonLogTypeColors, type == null ? null : type.name());
        if (configured == null && type != null) {
            configured = configuredValue(logging.jsonLogTypeColors, typeKey(type));
        }
        if (configured == null) {
            configured = configuredValue(logging.jsonLogColors, event.level().name());
        }
        if (configured == null) {
            configured = configuredValue(logging.jsonLogColors, event.level().name().toLowerCase(Locale.ROOT));
        }

        return toAnsiStyle(configured);
    }

    private String highlight(String body, LogType type, LogEvent event, String base) {
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
    private String highlightSql(String body, LogEvent event, String base) {
        String sql = event.sql();
        if (sql == null || sql.isBlank()) {
            return body;
        }
        String color = highlightColor(SQL_HIGHLIGHT);
        if (color == null) {
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
        return wrap(body, valueStart, valueLength, color, base);
    }

    private String highlightRows(String body, LogEvent event, String base) {
        Long rows = event.rowsAffected();
        if (rows == null) {
            return body;
        }
        String color = highlightColor(ROWS_HIGHLIGHT);
        if (color == null) {
            return body;
        }
        return highlightNumber(body, "rows_affected", Long.toString(rows), color, base);
    }

    private String highlightStatus(String body, LogEvent event, String base) {
        Integer status = event.status();
        if (status == null) {
            return body;
        }
        String color = highlightColor(
                status >= STATUS_ERROR_THRESHOLD ? STATUS_ERROR_HIGHLIGHT : STATUS_OK_HIGHLIGHT);
        if (color == null) {
            return body;
        }
        return highlightNumber(body, "status", Integer.toString(status), color, base);
    }

    private String highlightColor(String key) {
        String configured = configuredValue(logging.jsonLogHighlightColors, key);
        return toAnsiStyle(configured);
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

    private static String toAnsiStyle(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String key = configured.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        String directColor = ANSI_COLORS.get(key);
        if (directColor != null) {
            return directColor;
        }
        StringBuilder style = new StringBuilder();
        boolean hasColor = false;
        for (String token : configured.trim().split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            String normalized = token.trim().replace('-', '_').toUpperCase(Locale.ROOT);
            if ("BOLD".equals(normalized)) {
                style.append(BOLD);
                continue;
            }
            if ("DIM".equals(normalized) || "DIMMED".equals(normalized)) {
                style.append(DIMMED);
                continue;
            }
            String color = ANSI_COLORS.get(normalized);
            if (color != null) {
                style.append(color);
                hasColor = true;
            }
        }
        return hasColor ? style.toString() : null;
    }

    private static String configuredValue(Map<String, String> values, String key) {
        if (values == null || key == null) {
            return null;
        }
        String configured = values.get(key);
        if (configured != null) {
            return configured;
        }
        String normalizedKey = normalizeKey(key);
        return values.entrySet().stream()
                .filter(entry -> normalizeKey(entry.getKey()).equals(normalizedKey))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String typeKey(LogType type) {
        String[] words = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder key = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            key.append(words[i].substring(0, 1).toUpperCase(Locale.ROOT)).append(words[i].substring(1));
        }
        return key.toString();
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
