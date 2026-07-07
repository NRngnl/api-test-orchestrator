package io.vtz.apitest.application.service;

import io.vtz.apitest.domain.db.FixturePolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public class FixtureRowPreparer {
    private final FixturePolicy policy;
    private final IntFunction<String> stringFactory;

    public FixtureRowPreparer(FixturePolicy policy, IntFunction<String> stringFactory) {
        this.policy = policy == null ? FixturePolicy.none() : policy;
        this.stringFactory = stringFactory;
    }

    public Map<String, Object> prepare(String table, Map<String, Object> row, List<String> ignoreKeys) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.putAll(policy.generatedValuesFor(table, stringFactory));
        values.putAll(policy.defaultsFor(table));
        if (row != null) {
            values.putAll(row);
        }
        if (ignoreKeys != null) {
            ignoreKeys.forEach(values::remove);
        }
        return values;
    }
}
