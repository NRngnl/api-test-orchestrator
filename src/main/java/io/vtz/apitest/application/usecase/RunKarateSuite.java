package io.vtz.apitest.application.usecase;

import io.vtz.apitest.application.port.ApiProcessPort;
import io.vtz.apitest.application.port.KarateRunnerPort;
import io.vtz.apitest.domain.process.ApiProcessSpec;
import io.vtz.apitest.domain.test.TestRunRequest;
import io.vtz.apitest.domain.test.TestRunResult;

public class RunKarateSuite {
    private final KarateRunnerPort karateRunner;
    private final ApiProcessPort apiProcess;

    public RunKarateSuite(KarateRunnerPort karateRunner, ApiProcessPort apiProcess) {
        this.karateRunner = karateRunner;
        this.apiProcess = apiProcess;
    }

    public TestRunResult run(TestRunRequest request, ApiProcessSpec apiSpec) {
        if (apiSpec == null) {
            return karateRunner.run(request);
        }
        apiProcess.start(apiSpec, event -> {
        }, line -> {
        });
        try {
            if (!apiProcess.awaitReady(apiSpec)) {
                throw new IllegalStateException("API process did not become ready: " + apiSpec.healthUrl());
            }
            return karateRunner.run(request);
        } finally {
            apiProcess.stop();
        }
    }
}
