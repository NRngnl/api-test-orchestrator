package io.vtz.apitest.domain.db;

import java.util.Map;

public record InsertResult(int affectedRows, Map<String, Object> generatedKeys, Map<String, Object> row) {
}
