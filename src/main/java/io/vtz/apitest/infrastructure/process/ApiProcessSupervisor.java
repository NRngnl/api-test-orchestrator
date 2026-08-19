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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ApiProcessSupervisor implements ApiProcessPort {
    private static final Duration GRACEFUL_STOP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration FORCED_STOP_TIMEOUT = Duration.ofSeconds(5);

    private final JsonLogParser logParser = new JsonLogParser();
    private Process process;
    private AsyncSink<LogEvent> stdoutSink;
    private AsyncSink<String> stderrSink;

    @Override
    public synchronized void start(ApiProcessSpec spec, Consumer<LogEvent> stdout, Consumer<String> stderr) {
        if (process != null && !process.isAlive()) {
            process = null;
            closeSinks();
        }
        if (process != null) {
            throw new IllegalStateException("API process is already running");
        }
        Process started = null;
        AsyncSink<LogEvent> startedStdoutSink = null;
        AsyncSink<String> startedStderrSink = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(spec.command());
            if (spec.workingDir() != null) {
                builder.directory(spec.workingDir().toFile());
            }
            builder.environment().putAll(spec.environment());
            started = builder.start();
            startedStdoutSink = new AsyncSink<>("ato-api-stdout-log-consumer", stdout);
            startedStderrSink = new AsyncSink<>("ato-api-stderr-log-consumer", stderr);
            process = started;
            stdoutSink = startedStdoutSink;
            stderrSink = startedStderrSink;
            Process runningProcess = started;
            AsyncSink<LogEvent> runningStdoutSink = startedStdoutSink;
            AsyncSink<String> runningStderrSink = startedStderrSink;
            Thread.ofVirtual().name("ato-api-stdout-reader").start(() -> readStdout(runningProcess, runningStdoutSink));
            Thread.ofVirtual().name("ato-api-stderr-reader").start(() -> readStderr(runningProcess, runningStderrSink));
        } catch (Exception e) {
            cleanupFailedStart(started, startedStdoutSink, startedStderrSink);
            throw new IllegalStateException("Failed to start API process: " + spec.command(), e);
        }
    }

    @Override
    public boolean awaitReady(ApiProcessSpec spec) {
        Instant deadline = Instant.now().plus(spec.healthTimeout());
        while (Instant.now().isBefore(deadline)) {
            if (Thread.currentThread().isInterrupted() || hasExited()) {
                return false;
            }
            if (canConnect(spec)) {
                return true;
            }
            if (!sleep(spec.healthInterval())) {
                return false;
            }
        }
        return false;
    }

    @Override
    public synchronized int stop() {
        if (process == null) {
            return 0;
        }
        Process stopping = process;
        stopping.destroy();
        try {
            if (!stopping.waitFor(GRACEFUL_STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                stopping.destroyForcibly();
                if (!stopping.waitFor(FORCED_STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    return 1;
                }
            }
            return stopping.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopping.destroyForcibly();
            return 1;
        } finally {
            if (!stopping.isAlive()) {
                process = null;
                closeSinks();
            }
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

    private synchronized boolean hasExited() {
        return process == null || !process.isAlive();
    }

    private void readStdout(Process runningProcess, AsyncSink<LogEvent> sink) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sink.accept(logParser.parse(line));
            }
        } catch (Exception ignored) {
        } finally {
            sink.close();
        }
    }

    private void readStderr(Process runningProcess, AsyncSink<String> sink) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sink.accept(line);
            }
        } catch (Exception ignored) {
        } finally {
            sink.close();
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

    private void closeSinks() {
        if (stdoutSink != null) {
            stdoutSink.close();
            stdoutSink = null;
        }
        if (stderrSink != null) {
            stderrSink.close();
            stderrSink = null;
        }
    }

    private void cleanupFailedStart(
            Process started,
            AsyncSink<LogEvent> startedStdoutSink,
            AsyncSink<String> startedStderrSink) {
        if (started != null && started.isAlive()) {
            started.destroyForcibly();
        }
        if (startedStdoutSink != null) {
            startedStdoutSink.close();
        }
        if (startedStderrSink != null) {
            startedStderrSink.close();
        }
        if (process == started) {
            process = null;
            stdoutSink = null;
            stderrSink = null;
        }
    }

    private static boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
