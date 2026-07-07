package io.vtz.apitest.infrastructure.log;

import io.vtz.apitest.domain.log.LogEvent;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RequestLogCorrelator {
    private final Map<String, List<LogEvent>> byRequestId = new LinkedHashMap<>();
    private final Map<String, String> uriToRequestId = new LinkedHashMap<>();
    private String lastRequestId;

    public synchronized void accept(LogEvent event) {
        event.requestIdOptional().ifPresent(requestId -> {
            byRequestId.computeIfAbsent(requestId, ignored -> new ArrayList<>()).add(event);
            lastRequestId = requestId;
            if ("REQUEST".equals(event.message()) && event.uri() != null) {
                uriToRequestId.put(event.uri(), requestId);
            }
        });
    }

    public synchronized Optional<List<LogEvent>> findByFailureUrl(String failureUrl) {
        String path = pathAndQuery(failureUrl);
        String requestId = uriToRequestId.get(path);
        if (requestId == null) {
            requestId = uriToRequestId.entrySet().stream()
                    .filter(entry -> entry.getKey().contains(path) || path.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return requestId == null ? Optional.empty() : Optional.ofNullable(byRequestId.get(requestId));
    }

    public synchronized List<LogEvent> lastRequestLogs() {
        if (lastRequestId == null) {
            return List.of();
        }
        return List.copyOf(byRequestId.getOrDefault(lastRequestId, List.of()));
    }

    private static String pathAndQuery(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
        } catch (Exception ignored) {
            return value;
        }
    }
}
