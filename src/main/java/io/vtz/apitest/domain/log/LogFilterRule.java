package io.vtz.apitest.domain.log;

import java.util.List;
import java.util.regex.Pattern;

public record LogFilterRule(LogLevel minimumLevel, List<Pattern> include, List<Pattern> exclude) {
    public LogFilterRule {
        minimumLevel = minimumLevel == null ? LogLevel.ALL : minimumLevel;
        include = List.copyOf(include == null ? List.of() : include);
        exclude = List.copyOf(exclude == null ? List.of() : exclude);
    }

    public boolean includes(LogEvent event) {
        if (event == null) {
            return false;
        }
        if (!minimumLevel.allows(event.level())) {
            return false;
        }
        String searchable = event.searchableText();
        if (exclude.stream().anyMatch(pattern -> pattern.matcher(searchable).find())) {
            return false;
        }
        return include.isEmpty() || include.stream().anyMatch(pattern -> pattern.matcher(searchable).find());
    }
}
