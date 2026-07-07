package io.vtz.apitest.domain.mock;

public record RunningMockServer(String id, String feature, int port, boolean ssl, String url) {
}
