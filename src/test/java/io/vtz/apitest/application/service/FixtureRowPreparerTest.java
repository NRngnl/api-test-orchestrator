package io.vtz.apitest.application.service;

import io.vtz.apitest.domain.db.FixturePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FixtureRowPreparerTest {
    @Test
    void appliesConfiguredTableDefaultsWithoutOverwritingScenarioValues() {
        FixtureRowPreparer preparer = new FixtureRowPreparer(
                fixturePolicy(),
                length -> "generated-fixture-key");

        Map<String, Object> row = preparer.prepare(
                "cases",
                Map.of("business_date", "2026-07-07", "tenant_id", 99),
                List.of());

        assertEquals("generated-fixture-key", row.get("fixture_key"));
        assertEquals(99, row.get("tenant_id"));
        assertEquals(2, row.get("created_by"));
        assertEquals("OPEN", row.get("status"));
        assertEquals("2026-07-07", row.get("business_date"));
    }

    @Test
    void removesIgnoredKeysAfterDefaultsAreApplied() {
        FixtureRowPreparer preparer = new FixtureRowPreparer(
                fixturePolicy(),
                length -> "generated-fixture-key");

        Map<String, Object> row = preparer.prepare(
                "cases",
                Map.of("owner_id", 10),
                List.of("fixture_key", "created_by"));

        assertFalse(row.containsKey("fixture_key"));
        assertFalse(row.containsKey("created_by"));
        assertEquals(7, row.get("tenant_id"));
        assertEquals("OPEN", row.get("status"));
        assertEquals(10, row.get("owner_id"));
    }

    @Test
    void onlyAppliesRulesForTablesThatNeedThem() {
        FixtureRowPreparer preparer = new FixtureRowPreparer(
                fixturePolicy(),
                length -> "generated-fixture-key");

        Map<String, Object> row = preparer.prepare(
                "users",
                Map.of("name", "T1"),
                List.of());

        assertFalse(row.containsKey("fixture_key"));
        assertFalse(row.containsKey("status"));
        assertEquals(7, row.get("tenant_id"));
        assertEquals("T1", row.get("name"));
    }

    private static FixturePolicy fixturePolicy() {
        return new FixturePolicy(
                Map.of("tenant_id", 7),
                Map.of("cases", Map.of("created_by", 2, "status", "OPEN")),
                Map.of("fixture_key", new FixturePolicy.GeneratedStringColumn(Set.of("cases"), 16)));
    }
}
