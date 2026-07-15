package io.vtz.apitest.infrastructure.karate;

import io.vtz.apitest.application.port.MockServerPort;
import io.vtz.apitest.domain.mock.MockServerSpec;
import io.vtz.apitest.domain.mock.RunningMockServer;
import io.karatelabs.core.MockHandler;
import io.karatelabs.core.MockServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class KarateMockServerRegistry implements MockServerPort {
    private static final Logger LOG = LoggerFactory.getLogger(KarateMockServerRegistry.class);

    // "Wait it out": on a fixed-port re-bind the kernel can hold the port in TIME_WAIT for up to
    // ~60s (Linux) before a new bind() succeeds. Wait up to this cap, polling, so a transient
    // collision becomes a bounded wait instead of a crash. Lower BIND_WAIT_MILLIS to fail fast.
    // Intentionally NOT runtime-config: the registry is a process-wide singleton, so a mutable
    // per-instance policy could be silently overwritten across orchestrators. If per-run tuning is
    // ever needed, carry it on MockServerSpec (per start), never as shared mutable state.
    private static final long BIND_WAIT_MILLIS = 70_000;
    private static final long BIND_POLL_INTERVAL_MILLIS = 500;
    private static final int ACTIVE_LISTENER_PROBE_TIMEOUT_MILLIS = 200;

    private final Map<String, Entry> servers = new LinkedHashMap<>();   // guarded by `this`
    // Bumped by every stopAll(). A start captures it before its (lock-free) port wait and re-checks it
    // both DURING the wait and — atomically with the actual bind — while holding the monitor, so a bind
    // can never complete and register a listener after a concurrent stopAll has already returned.
    // volatile so the wait loop can observe a bump without taking the monitor.
    private volatile long stopAllGeneration;
    // Per fixed-port lifecycle lock. EVERY start/stop/replace on a port takes it, so those operations
    // are fully serialized on that port (no start racing a still-shutting-down listener). stopAll does
    // NOT take it (it coordinates via the generation) to avoid a port-lock ↔ port-wait deadlock.
    private final ConcurrentMap<Integer, ReentrantLock> portLocks = new ConcurrentHashMap<>();

    @Override
    public RunningMockServer start(MockServerSpec spec) {
        if (spec.port() == 0) {
            // Ephemeral port: the OS picks a free one, no serialization or wait needed.
            return bindAndRegister(spec, stopAllGeneration);
        }
        ReentrantLock portLock = portLockFor(spec.port());
        portLock.lock();
        try {
            List<Entry> superseded;
            long generation;
            synchronized (this) {
                superseded = removeServersOnFixedPort(spec.port());
                generation = stopAllGeneration;
            }
            stopServersOrRestore(superseded);   // stop old mocks on this port (we hold the port lock)
            awaitPortBindable(spec.port(), generation);
            return bindAndRegister(spec, generation);
        } finally {
            portLock.unlock();
        }
    }

    // Binds and registers ATOMICALLY with respect to stopAll: the generation re-check, the bind, and the
    // map insert all happen under the monitor, and stopAll bumps the generation + clears the map under
    // the same monitor. So either stopAll wins (this aborts and binds nothing) or this wins (stopAll
    // then sees and stops the registered server) — a listener can never survive a returned stopAll.
    private RunningMockServer bindAndRegister(MockServerSpec spec, long generation) {
        synchronized (this) {
            if (stopAllGeneration != generation) {
                throw new IllegalStateException(
                        "mock server start on port " + spec.port() + " aborted by a concurrent stopAll");
            }
            MockServer server;
            try {
                server = buildServer(spec).start();
            } catch (Exception e) {
                if (isAddressInUse(e)) {
                    // Something claimed the port between the free-probe and this bind. Do NOT retry: a
                    // failed Karate start() leaks its Netty event-loop groups, so fail fast instead.
                    throw new IllegalStateException(
                            "mock server port " + spec.port() + " was taken between the free-probe and the bind", e);
                }
                // Non-bind startup failure (bad feature, unreadable TLS cert, ...): surface it unchanged.
                throw e instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new IllegalStateException("failed to start mock server on port " + spec.port(), e);
            }
            RunningMockServer running = new RunningMockServer(
                    UUID.randomUUID().toString(), spec.feature(), server.getPort(), server.isSsl(), server.getUrl());
            servers.put(running.id(), new Entry(server, running));
            return running;
        }
    }

    @Override
    public void stop(String id) {
        int port;
        synchronized (this) {
            Entry entry = servers.get(id);
            if (entry == null) {
                return;
            }
            port = entry.running().port();
        }
        // Serialize teardown with startFixedPort on the same port so a concurrent start never observes
        // an empty registry while the old listener is still shutting down.
        ReentrantLock portLock = port == 0 ? null : portLockFor(port);
        if (portLock != null) {
            portLock.lock();
        }
        try {
            Entry entry;
            synchronized (this) {
                entry = servers.remove(id);
            }
            if (entry == null) {
                return;
            }
            try {
                entry.server().stopAndWait();
            } catch (RuntimeException e) {
                synchronized (this) {
                    servers.putIfAbsent(id, entry);   // restore so the failure can be retried, not orphaned
                }
                throw e;
            }
        } finally {
            if (portLock != null) {
                portLock.unlock();
            }
        }
    }

    @Override
    public void stopAll() {
        List<Entry> toStop;
        synchronized (this) {
            stopAllGeneration++;   // in-flight starts see this and abort before/at their bind
            toStop = new ArrayList<>(servers.values());
            servers.clear();
        }
        // Attempt EVERY shutdown (stopAndWait blocks ~2s each and needs no shared state); collect
        // failures and re-register the ones that did not stop so stopAll() stays retry-safe rather than
        // orphaning live servers whose references were dropped.
        List<Entry> failed = new ArrayList<>();
        List<RuntimeException> errors = new ArrayList<>();
        for (Entry entry : toStop) {
            try {
                entry.server().stopAndWait();
            } catch (RuntimeException e) {
                failed.add(entry);
                errors.add(e);
            }
        }
        if (!failed.isEmpty()) {
            synchronized (this) {
                failed.forEach(entry -> servers.putIfAbsent(entry.running().id(), entry));
            }
            IllegalStateException aggregate =
                    new IllegalStateException("failed to stop " + failed.size() + " mock server(s)");
            errors.forEach(aggregate::addSuppressed);
            throw aggregate;
        }
    }

    @Override
    public synchronized List<RunningMockServer> runningServers() {
        return servers.values().stream().map(Entry::running).toList();
    }

    @Override
    public void setVariable(int port, String key, Object value) {
        MockServer server = serverOnPort(port);
        if (server == null) {
            return;
        }
        withRequestLock(port, server, () -> server.getHandler().getGlobals().put(key, value));
    }

    @Override
    public Object getVariable(int port, String key) {
        MockServer server = serverOnPort(port);
        if (server == null) {
            return null;
        }
        return withRequestLock(port, server, () -> server.getHandler().getVariable(key));
    }

    // Resolve the target server under the monitor, RELEASE the monitor, THEN take Karate's requestLock.
    // Never hold the registry monitor while waiting on requestLock: a mock feature (Java bridge) runs
    // under requestLock and may call back into the registry (runningServers/stop) needing the monitor,
    // so holding both in the opposite order would deadlock.
    private <T> T withRequestLock(int port, MockServer server, java.util.function.Supplier<T> action) {
        ReentrantLock lock = requestLockOf(server);
        if (lock == null) {
            // Fail loud rather than mutate MockHandler's plain LinkedHashMap unguarded (that would risk
            // a ConcurrentModificationException / lost write against the request thread).
            throw new IllegalStateException("cannot safely access mock variables on port " + port
                    + ": Karate MockHandler.requestLock is unavailable (dependency skew?)");
        }
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private synchronized MockServer serverOnPort(int port) {
        Entry entry = findByPort(port);
        return entry == null ? null : entry.server();
    }

    private void awaitPortBindable(int port, long generation) {
        long deadlineNanos = System.nanoTime() + BIND_WAIT_MILLIS * 1_000_000L;
        while (!isPortFree(port)) {
            if (stopAllGeneration != generation) {
                throw new IllegalStateException(
                        "mock server start on port " + port + " aborted by a concurrent stopAll");
            }
            // Best-effort fast-fail: if something ACCEPTS a connection here it is a live listener, a
            // permanent conflict. (A listener bound to a non-loopback interface won't answer this
            // loopback probe; we then poll to the deadline and error — bounded and safe, just not instant.)
            if (hasActiveListener(port)) {
                throw new IllegalStateException("mock server port " + port
                        + " is held by an active listener (permanent conflict, not a transient TIME_WAIT)");
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new IllegalStateException(
                        "mock server port " + port + " still in use after " + BIND_WAIT_MILLIS + "ms");
            }
            long remainingMillis = remainingNanos / 1_000_000L;
            long napMillis = Math.min(BIND_POLL_INTERVAL_MILLIS, Math.max(1, remainingMillis));
            LOG.warn("mock server port {} in TIME_WAIT; waiting to bind (remaining {}ms)", port, remainingMillis);
            sleep(napMillis);
        }
    }

    // Package-private for tests. Mirrors Karate/Netty's NioServerSocketChannel: same channel type, and
    // does NOT touch SO_REUSEADDR — inherits the platform default Netty inherits, so it reports "free"
    // exactly when the real bind would succeed (in particular "in use" while a TIME_WAIT socket lingers).
    static boolean isPortFree(int port) {
        try (ServerSocketChannel probe = ServerSocketChannel.open()) {
            probe.bind(new InetSocketAddress(port));
            return true;
        } catch (BindException e) {
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("failed probing mock server port " + port, e);
        }
    }

    // Package-private for tests. Best-effort: a completed connection means a live listener owns the
    // port (permanent conflict); a refused connection means no reachable listener (e.g. TIME_WAIT).
    static boolean hasActiveListener(int port) {
        try (SocketChannel probe = SocketChannel.open()) {
            probe.socket().connect(new InetSocketAddress("127.0.0.1", port), ACTIVE_LISTENER_PROBE_TIMEOUT_MILLIS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Karate's MockHandler guards each request with a private ReentrantLock (apply() locks it around
    // the whole request). We reflect it once so a variable update can be made atomic with request
    // handling. Resolved at class load; setVariable/getVariable fail fast if it is ever unavailable.
    private static final Field REQUEST_LOCK_FIELD = resolveRequestLockField();

    private static Field resolveRequestLockField() {
        try {
            Field field = MockHandler.class.getDeclaredField("requestLock");
            field.setAccessible(true);
            return field;
        } catch (RuntimeException | ReflectiveOperationException e) {
            LOG.warn("MockHandler.requestLock unavailable; setMockVariable/getMockVariable will fail fast", e);
            return null;
        }
    }

    private static ReentrantLock requestLockOf(MockServer server) {
        if (REQUEST_LOCK_FIELD == null) {
            return null;
        }
        try {
            return (ReentrantLock) REQUEST_LOCK_FIELD.get(server.getHandler());
        } catch (RuntimeException | ReflectiveOperationException e) {
            return null;
        }
    }

    private ReentrantLock portLockFor(int port) {
        return portLocks.computeIfAbsent(port, key -> new ReentrantLock());
    }

    private MockServer.Builder buildServer(MockServerSpec spec) {
        MockServer.Builder builder = MockServer.feature(spec.feature())
                .port(spec.port())
                .ssl(spec.ssl())
                .javaBridgeEnabled(spec.javaBridgeEnabled())
                .requestExpressionsEnabled(spec.requestExpressionsEnabled())
                .arg(spec.args());

        if (spec.pathPrefix() != null && !spec.pathPrefix().isBlank()) {
            builder.pathPrefix(spec.pathPrefix());
        }
        if (spec.certPath() != null && !spec.certPath().isBlank()) {
            builder.certPath(spec.certPath());
        }
        if (spec.keyPath() != null && !spec.keyPath().isBlank()) {
            builder.keyPath(spec.keyPath());
        }
        return builder;
    }

    // Caller holds the port lock and the registry monitor. Removes (does not stop) registered servers.
    private List<Entry> removeServersOnFixedPort(int port) {
        if (port == 0) {
            return List.of();
        }
        List<String> ids = servers.values().stream()
                .filter(entry -> entry.running().port() == port)
                .map(entry -> entry.running().id())
                .toList();
        List<Entry> removed = new ArrayList<>();
        for (String id : ids) {
            Entry entry = servers.remove(id);
            if (entry != null) {
                removed.add(entry);
            }
        }
        return removed;
    }

    // Caller holds the port lock. Stops the superseded servers; restores any that fail so they aren't
    // orphaned.
    private void stopServersOrRestore(List<Entry> superseded) {
        for (Entry entry : superseded) {
            try {
                entry.server().stopAndWait();
            } catch (RuntimeException e) {
                synchronized (this) {
                    servers.putIfAbsent(entry.running().id(), entry);
                }
                throw e;
            }
        }
    }

    // Caller holds the registry monitor.
    private Entry findByPort(int port) {
        return servers.values().stream()
                .filter(entry -> entry.running().port() == port)
                .findFirst()
                .orElse(null);
    }

    private static boolean isAddressInUse(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof BindException) {
                return true;
            }
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for mock server port to free", e);
        }
    }

    private record Entry(MockServer server, RunningMockServer running) {
    }
}
