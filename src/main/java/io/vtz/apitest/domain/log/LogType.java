package io.vtz.apitest.domain.log;

/**
 * Classification of an API JSON log line by the keys and message patterns it carries.
 *
 * <p>Mirrors a reference log-classification implementation so the CLI can color logs according
 * to the key or pattern emitted by the API rather than by level alone.
 */
public enum LogType {
    API_ERROR,
    API_SQL,
    API_BODY_DUMP,
    API_REQUEST,
    API_GENERAL;

    private static final String BODY_DUMP_MARKER = "request / response body dump";
    private static final String REQUEST_MARKER = "REQUEST";
    private static final String SQL_MARKER = "SQL";

    /**
     * Classifies a log event by inspecting its message pattern and structured keys.
     *
     * <p>Precedence mirrors the reference implementation: SQL detection first (errored SQL is an
     * error), then body dumps, then request summaries, then a bare error level, else general.
     *
     * <p>Notes on deliberate behavior: the SQL marker is a case-sensitive substring match on the
     * message (so {@code "SQL"} anywhere, e.g. {@code "MySQL"}, matches) and error detection uses
     * the parsed {@link LogLevel}, which is case-insensitive (a lowercase {@code "error"} level
     * still classifies as an error). Both are intentional and only affect console log coloring.
     */
    public static LogType classify(LogEvent event) {
        if (event == null) {
            return API_GENERAL;
        }
        String message = event.message() == null ? "" : event.message();
        boolean errored = event.level() == LogLevel.ERROR || notBlank(event.error());

        if (message.contains(SQL_MARKER) || notBlank(event.sql())) {
            return errored ? API_ERROR : API_SQL;
        }
        if (message.contains(BODY_DUMP_MARKER)) {
            return API_BODY_DUMP;
        }
        if (REQUEST_MARKER.equals(message)) {
            return API_REQUEST;
        }
        if (event.level() == LogLevel.ERROR) {
            return API_ERROR;
        }
        return API_GENERAL;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
