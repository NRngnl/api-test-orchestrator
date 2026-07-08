package io.vtz.apitest.application.port;

import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.process.CommandResult;
import io.vtz.apitest.domain.process.ExecCommandSpec;

import java.util.function.Consumer;

public interface CommandRunnerPort {
    CommandResult run(ExecCommandSpec spec, Consumer<LogEvent> onLogLine);
}
