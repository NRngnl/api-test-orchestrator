package io.vtz.apitest.domain.log;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFilterRuleTest {
    @Test
    void filtersByLevelAndPatterns() {
        LogFilterRule rule = new LogFilterRule(
                LogLevel.WARN,
                List.of(Pattern.compile("payment")),
                List.of(Pattern.compile("health")));

        assertTrue(rule.includes(event(LogLevel.ERROR, "payment failed")));
        assertFalse(rule.includes(event(LogLevel.INFO, "payment info")));
        assertFalse(rule.includes(event(LogLevel.ERROR, "health payment")));
        assertFalse(rule.includes(event(LogLevel.ERROR, "other failure")));
    }

    private static LogEvent event(LogLevel level, String raw) {
        return new LogEvent(Instant.now(), level, raw, raw, null, null, null, null, null, null, null);
    }
}
