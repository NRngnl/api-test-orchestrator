package io.vtz.apitest.domain.log;

public enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ALL;

    public static LogLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return switch (value.trim().toUpperCase()) {
            case "DEBUG" -> DEBUG;
            case "INFO" -> INFO;
            case "WARN", "WARNING" -> WARN;
            case "ERROR" -> ERROR;
            default -> ALL;
        };
    }

    public boolean allows(LogLevel actual) {
        if (this == ALL || actual == ALL) {
            return true;
        }
        return actual.ordinal() >= this.ordinal();
    }
}
