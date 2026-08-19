package io.vtz.apitest.infrastructure.process;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class AsyncSink<T> implements AutoCloseable {
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(2);
    private static final int QUEUE_CAPACITY = 8_192;

    private final Consumer<T> consumer;
    private final BlockingQueue<T> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CountDownLatch drained = new CountDownLatch(1);
    private final Thread worker;

    AsyncSink(String name, Consumer<T> consumer) {
        this.consumer = consumer;
        this.worker = consumer == null
                ? null
                : Thread.ofVirtual().name(name).start(this::drain);
        if (consumer == null) {
            drained.countDown();
        }
    }

    void accept(T item) {
        if (consumer != null && item != null && !closed.get()) {
            // Live streaming is best-effort; callers keep their authoritative output separately.
            queue.offer(item);
        }
    }

    private void drain() {
        try {
            while (!closed.get() || !queue.isEmpty()) {
                T item = queue.poll(100, TimeUnit.MILLISECONDS);
                if (item != null) {
                    try {
                        consumer.accept(item);
                    } catch (RuntimeException ignored) {
                        // Keep pipe-drain isolation even if a display/log sink fails.
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            drained.countDown();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!drained.await(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS) && worker != null) {
                worker.interrupt();
            }
        } catch (InterruptedException e) {
            if (worker != null) {
                worker.interrupt();
            }
            Thread.currentThread().interrupt();
        }
    }
}
