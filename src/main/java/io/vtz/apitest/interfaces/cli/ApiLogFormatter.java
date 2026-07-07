package io.vtz.apitest.interfaces.cli;

import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.infrastructure.config.FrameworkConfig;

import java.util.Locale;
import java.util.Map;

final class ApiLogFormatter {
    private static final String RESET = "\u001B[0m";
    private static final Map<String, String> ANSI_COLORS = Map.ofEntries(
            Map.entry("BLACK", "\u001B[30m"),
            Map.entry("RED", "\u001B[31m"),
            Map.entry("GREEN", "\u001B[32m"),
            Map.entry("YELLOW", "\u001B[33m"),
            Map.entry("BLUE", "\u001B[34m"),
            Map.entry("MAGENTA", "\u001B[35m"),
            Map.entry("CYAN", "\u001B[36m"),
            Map.entry("WHITE", "\u001B[37m"),
            Map.entry("BRIGHT_BLACK", "\u001B[90m"),
            Map.entry("BRIGHT_RED", "\u001B[91m"),
            Map.entry("BRIGHT_GREEN", "\u001B[92m"),
            Map.entry("BRIGHT_YELLOW", "\u001B[93m"),
            Map.entry("BRIGHT_BLUE", "\u001B[94m"),
            Map.entry("BRIGHT_MAGENTA", "\u001B[95m"),
            Map.entry("BRIGHT_CYAN", "\u001B[96m"),
            Map.entry("BRIGHT_WHITE", "\u001B[97m"));

    private final FrameworkConfig.Logging logging;

    ApiLogFormatter(FrameworkConfig.Logging logging) {
        this.logging = logging == null ? new FrameworkConfig.Logging() : logging;
    }

    String apiLine(LogEvent event) {
        String line = "[api] " + event.raw();
        String ansi = ansiColor(event);
        return ansi == null ? line : ansi + line + RESET;
    }

    String indentedApiLine(LogEvent event) {
        String line = "  " + event.raw();
        String ansi = ansiColor(event);
        return ansi == null ? line : ansi + line + RESET;
    }

    private String ansiColor(LogEvent event) {
        if (!logging.colors || event == null || event.structured() == null || logging.jsonLogColors == null) {
            return null;
        }
        String configured = logging.jsonLogColors.get(event.level().name());
        if (configured == null) {
            configured = logging.jsonLogColors.get(event.level().name().toLowerCase(Locale.ROOT));
        }
        return toAnsiColor(configured);
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
