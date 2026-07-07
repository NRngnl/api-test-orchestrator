package io.vtz.apitest.domain.db;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

public record FixturePolicy(
        Map<String, Object> defaults,
        Map<String, Map<String, Object>> tableDefaults,
        Map<String, GeneratedStringColumn> generatedStringColumns
) {
    public FixturePolicy {
        defaults = Map.copyOf(defaults == null ? Map.of() : defaults);
        tableDefaults = copyTableDefaults(tableDefaults);
        generatedStringColumns = copyGeneratedStringColumns(generatedStringColumns);
    }

    public static FixturePolicy none() {
        return new FixturePolicy(Map.of(), Map.of(), Map.of());
    }

    public Map<String, Object> defaultsFor(String table) {
        Map<String, Object> values = new LinkedHashMap<>(defaults);
        Map<String, Object> perTable = tableDefaults.get(table);
        if (perTable != null) {
            values.putAll(perTable);
        }
        return values;
    }

    public Map<String, Object> generatedValuesFor(String table, IntFunction<String> stringFactory) {
        Map<String, Object> values = new LinkedHashMap<>();
        generatedStringColumns.forEach((column, policy) -> {
            if (policy.appliesTo(table)) {
                values.put(column, stringFactory.apply(policy.length()));
            }
        });
        return values;
    }

    private static Map<String, Map<String, Object>> copyTableDefaults(Map<String, Map<String, Object>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> copied = new LinkedHashMap<>();
        source.forEach((table, defaults) -> copied.put(table, Map.copyOf(defaults)));
        return Map.copyOf(copied);
    }

    private static Map<String, GeneratedStringColumn> copyGeneratedStringColumns(
            Map<String, GeneratedStringColumn> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(source);
    }

    public record GeneratedStringColumn(Set<String> tables, int length) {
        public GeneratedStringColumn {
            tables = Set.copyOf(tables == null ? Set.of() : tables);
            if (length < 1 || length > 32) {
                throw new IllegalArgumentException("generated string length must be between 1 and 32");
            }
        }

        private boolean appliesTo(String table) {
            return tables.contains(table);
        }
    }
}
