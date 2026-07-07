package io.vtz.apitest.infrastructure.process;

import io.vtz.apitest.application.port.ApiProcessPort;
import io.vtz.apitest.domain.log.LogEvent;
import io.vtz.apitest.domain.process.ApiProcessSpec;
import io.vtz.apitest.infrastructure.log.JsonLogParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public class ApiProcessSupervisor implements ApiProcessPort {
    private final JsonLogParser logParser = new JsonLogParser();
    private Process process;

    @Override
    public synchronized void start(ApiProcessSpec spec, Consumer<LogEvent> stdout, Consumer<String> stderr) {
        if (isRunning()) {
            throw new IllegalStateException("API process is already running");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(spec.command());
            if (spec.workingDir() != null) {
                builder.directory(spec.workingDir().toFile());
            }
            builder.environment().putAll(spec.environment());
            process = builder.start();
            Thread.ofVirtual().start(() -> readStdout(stdout));
            Thread.ofVirtual().start(() -> readStderr(stderr));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start API process: " + spec.command(), e);
        }
    }

    @Override
    public boolean awaitReady(ApiProcessSpec spec) {
        Instant deadline = Instant.now().plus(spec.healthTimeout());
        while (Instant.now().isBefore(deadline)) {
            if (canConnect(spec)) {
                return true;
            }
            sleep(spec.healthInterval());
        }
        return false;
    }

    @Override
    public synchronized int stop() {
        if (process == null) {
            return 0;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return 1;
        } finally {
            process = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    @Override
    public void close() {
        stop();
    }

    private void readStdout(Consumer<LogEvent> sink) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sink.accept(logParser.parse(line));
            }
        } catch (Exception ignored) {
        }
    }

    private void readStderr(Consumer<String> sink) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sink.accept(line);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean canConnect(ApiProcessSpec spec) {
        String host = spec.healthUrl().getHost();
        int port = spec.healthUrl().getPort() == -1 ? 80 : spec.healthUrl().getPort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
