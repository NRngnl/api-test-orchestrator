package io.vtz.apitest.infrastructure.karate;

import io.vtz.apitest.application.port.MockServerPort;
import io.vtz.apitest.domain.mock.MockServerSpec;
import io.vtz.apitest.domain.mock.RunningMockServer;
import io.karatelabs.core.MockServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KarateMockServerRegistry implements MockServerPort {
    private final Map<String, Entry> servers = new LinkedHashMap<>();

    @Override
    public synchronized RunningMockServer start(MockServerSpec spec) {
        stopExistingServerOnFixedPort(spec.port());

        MockServer.Builder builder = MockServer.feature(spec.feature())
                .port(spec.port())
                .ssl(spec.ssl())
                .javaBridgeEnabled(spec.javaBridgeEnabled())
                .requestExpressionsEnabled(spec.requestExpressionsEnabled())
                .arg(spec.args());

        if (spec.pathPrefix() != null && !spec.pathPrefix().isBlank()) {
            builder.pathPrefix(spec.pathPrefix());
        }
        if (spec.certPath() != null && !spec.certPath().isBlank()) {
            builder.certPath(spec.certPath());
        }
        if (spec.keyPath() != null && !spec.keyPath().isBlank()) {
            builder.keyPath(spec.keyPath());
        }

        MockServer server = builder.start();
        String id = UUID.randomUUID().toString();
        RunningMockServer running = new RunningMockServer(id, spec.feature(), server.getPort(), server.isSsl(), server.getUrl());
        servers.put(id, new Entry(server, running));
        return running;
    }

    @Override
    public synchronized void stop(String id) {
        Entry entry = servers.remove(id);
        if (entry != null) {
            entry.server.stopAndWait();
        }
    }

    @Override
    public synchronized void stopAll() {
        List<String> ids = new ArrayList<>(servers.keySet());
        ids.forEach(this::stop);
    }

    @Override
    public synchronized List<RunningMockServer> runningServers() {
        return servers.values().stream().map(Entry::running).toList();
    }

    private void stopExistingServerOnFixedPort(int port) {
        if (port == 0) {
            return;
        }
        runningServers().stream()
                .filter(server -> server.port() == port)
                .map(RunningMockServer::id)
                .toList()
                .forEach(this::stop);
    }

    private record Entry(MockServer server, RunningMockServer running) {
    }
}
