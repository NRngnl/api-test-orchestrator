package io.vtz.apitest.application.port;

import io.vtz.apitest.domain.test.TestRunRequest;
import io.vtz.apitest.domain.test.TestRunResult;

public interface KarateRunnerPort {
    TestRunResult run(TestRunRequest request);
}
