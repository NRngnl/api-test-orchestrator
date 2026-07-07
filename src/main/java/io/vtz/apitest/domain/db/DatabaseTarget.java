package io.vtz.apitest.domain.db;

public record DatabaseTarget(
        String jdbcUrl,
        String username,
        String password
) {
}
