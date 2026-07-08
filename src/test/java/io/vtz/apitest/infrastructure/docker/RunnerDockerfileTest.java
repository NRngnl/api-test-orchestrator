package io.vtz.apitest.infrastructure.docker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerDockerfileTest {
    @Test
    void exposesGodotenvInstallDirectoryOnRuntimePath() throws IOException {
        String dockerfile = Files.readString(Path.of("docker/runner/Dockerfile"));

        assertTrue(
                dockerfile.contains("COPY --from=godotenv /out/godotenv /usr/local/bin/godotenv"),
                "runner image should install godotenv under /usr/local/bin");
        assertTrue(
                dockerfile.contains("PATH=\"/usr/local/bin:"),
                "runner image should explicitly expose /usr/local/bin on PATH");
    }
}
