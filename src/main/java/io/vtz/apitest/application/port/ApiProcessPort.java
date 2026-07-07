package io.vtz.apitest.application.port;

import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.process.ApiProcessSpec;

import java.util.function.Consumer;

public interface ApiProcessPort extends AutoCloseable {
    void start(ApiProcessSpec spec, Consumer<LogEvent> stdout, Consumer<String> stderr);

    boolean awaitReady(ApiProcessSpec spec);

    int stop();

    boolean isRunning();

    @Override
    void close();
}
