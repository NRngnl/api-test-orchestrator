package io.vtz.apitest.application.port;

import io.vtz.apitest.domain.mock.MockServerSpec;
import io.vtz.apitest.domain.mock.RunningMockServer;

import java.util.List;

public interface MockServerPort {
    RunningMockServer start(MockServerSpec spec);

    void stop(String id);

    void stopAll();

    List<RunningMockServer> runningServers();
}
