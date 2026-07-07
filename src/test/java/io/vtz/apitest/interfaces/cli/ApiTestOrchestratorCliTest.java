package io.vtz.apitest.interfaces.cli;

import io.vtz.apitest.infrastructure.config.FrameworkConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiTestOrchestratorCliTest {
    @Test
    void noColorOverrideDisablesConfiguredColors() {
        ApiTestOrchestratorCli cli = new ApiTestOrchestratorCli();
        cli.noColor = true;
        FrameworkConfig config = new FrameworkConfig();
        config.logging.colors = true;

        cli.applyOverrides(config);

        assertFalse(config.logging.colors);
    }
}
