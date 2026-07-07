package io.vtz.apitest.application.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateFactory {
    public Date sqlDate(int year, int month, int day) {
        return Date.valueOf(LocalDate.of(year, month, day));
    }

    public Timestamp sqlTimestamp(int year, int month, int day, int hour, int minute, int second) {
        return Timestamp.valueOf(LocalDateTime.of(year, month, day, hour, minute, second));
    }

    public Date sqlDateFromString(String value) {
        return Date.valueOf(value);
    }

    public Timestamp sqlTimestampFromString(String value) {
        return Timestamp.valueOf(value);
    }

    public Timestamp sqlTimestampAdd(
            Timestamp timestamp,
            int years,
            int months,
            int days,
            int hours,
            int minutes,
            int seconds) {
        LocalDateTime adjusted = timestamp.toLocalDateTime()
                .plusYears(years)
                .plusMonths(months)
                .plusDays(days)
                .plusHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds);
        return Timestamp.valueOf(adjusted);
    }

    public String format(Object value, String pattern) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot format null date value");
        }
        if (value instanceof LocalDate localDate) {
            return localDate.format(DateTimeFormatter.ofPattern(pattern));
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.format(DateTimeFormatter.ofPattern(pattern));
        }
        if (value instanceof Instant instant) {
            return formatDate(java.util.Date.from(instant), pattern);
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return formatDate(java.util.Date.from(offsetDateTime.toInstant()), pattern);
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return formatDate(java.util.Date.from(zonedDateTime.toInstant()), pattern);
        }
        if (value instanceof java.util.Date date) {
            return formatDate(date, pattern);
        }
        if (value instanceof CharSequence text) {
            return format(parseText(text.toString()), pattern);
        }
        return format(parseText(value.toString()), pattern);
    }

    private String formatDate(java.util.Date date, String pattern) {
        return new SimpleDateFormat(pattern).format(date);
    }

    private Object parseText(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Cannot format blank date value");
        }
        if (normalized.length() == 10) {
            return Date.valueOf(normalized);
        }
        try {
            return Timestamp.valueOf(normalized.replace('T', ' ').replace("Z", ""));
        } catch (IllegalArgumentException ignored) {
            // Fall through to parsers that require timezone-aware ISO-8601 text.
        }
        try {
            return java.util.Date.from(Instant.parse(normalized));
        } catch (RuntimeException ignored) {
            // Fall through.
        }
        try {
            return java.util.Date.from(OffsetDateTime.parse(normalized).toInstant());
        } catch (RuntimeException ignored) {
            // Fall through.
        }
        return java.util.Date.from(LocalDateTime.parse(normalized)
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }
}
