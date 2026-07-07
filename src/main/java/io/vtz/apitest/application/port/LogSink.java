package io.vtz.apitest.application.port;

import io.vtz.apitest.domain.log.LogEvent;

public interface LogSink {
    void accept(LogEvent event);
}
