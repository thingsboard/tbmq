/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.standalone.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.exception.PipelineClosedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A fixed set of worker threads, each with its own queue. Items with the same key always go to the same worker, so
 * their order is kept. A worker takes what is waiting, up to a maximum batch, and hands it to the batch handler.
 * <p>
 * A handler that throws, even an {@link Error}, is logged and the worker goes on with the next batch; the handler
 * fails its own items.
 * Reading {@link #queued()} takes no lock, so it works while a handler is stuck in a slow call.
 */
@Slf4j
public class PartitionedWorkers<T> {

    private static final long SUBMIT_RETRY_MS = 100;

    private final String threadNamePrefix;
    private final int maxBatch;
    private final Consumer<List<T>> batchHandler;
    private final List<BlockingQueue<T>> queues;
    private final List<Thread> threads = new ArrayList<>();
    private final Object idleMonitor = new Object();
    // items submitted and not yet handled: waiting in a queue or inside the handler
    private final AtomicLong unfinished = new AtomicLong();

    private volatile boolean accepting = true;
    private volatile boolean stopped;
    private boolean started;

    /**
     * @param queueCapacity the capacity of each worker's queue; 0 means unbounded
     */
    public PartitionedWorkers(String threadNamePrefix, int partitions, int queueCapacity, int maxBatch, Consumer<List<T>> batchHandler) {
        if (partitions < 1) {
            throw new IllegalArgumentException("partitions must be at least 1, but was " + partitions);
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("queueCapacity must not be negative, but was " + queueCapacity);
        }
        if (maxBatch < 1) {
            throw new IllegalArgumentException("maxBatch must be at least 1, but was " + maxBatch);
        }
        this.threadNamePrefix = threadNamePrefix;
        this.maxBatch = maxBatch;
        this.batchHandler = batchHandler;
        this.queues = new ArrayList<>(partitions);
        for (int i = 0; i < partitions; i++) {
            queues.add(queueCapacity == 0 ? new LinkedBlockingQueue<>() : new LinkedBlockingQueue<>(queueCapacity));
        }
    }

    public synchronized void start() {
        if (started) {
            throw new IllegalStateException(threadNamePrefix + " workers are already started");
        }
        started = true;
        for (int i = 0; i < queues.size(); i++) {
            BlockingQueue<T> queue = queues.get(i);
            threads.add(Thread.ofPlatform().name(threadNamePrefix + "-" + i).daemon().start(() -> run(queue)));
        }
    }

    /**
     * Hands the item to the worker of the key and blocks while that worker's queue is full.
     *
     * @throws PipelineClosedException if {@link #drain} or {@link #stop} was called; the item is not taken
     * @throws InterruptedException     if the thread is interrupted while it waits for space; the item is not taken
     */
    public void submit(String key, T item) throws InterruptedException {
        BlockingQueue<T> queue = queues.get(Math.floorMod(key.hashCode(), queues.size()));
        // count first, then check: a drain that sees no unfinished items cannot miss an item admitted after it
        unfinished.incrementAndGet();
        try {
            requireAccepting();
            while (!queue.offer(item, SUBMIT_RETRY_MS, TimeUnit.MILLISECONDS)) {
                if (stopped) {
                    throw new PipelineClosedException(threadNamePrefix + " workers are stopped");
                }
            }
        } catch (InterruptedException | RuntimeException e) {
            finished(1);
            throw e;
        }
    }

    /**
     * Stops accepting and waits until every queue is empty and every handler has returned.
     *
     * @return {@code false} if that did not happen within the timeout; the workers go on and nothing is accepted
     */
    public boolean drain(long timeoutMs) {
        accepting = false;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        synchronized (idleMonitor) {
            while (unfinished.get() > 0) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMs <= 0) {
                    return false;
                }
                try {
                    idleMonitor.wait(remainingMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Stops accepting and interrupts the workers. Items still queued are dropped; call {@link #drain} first to keep them.
     */
    public synchronized void stop() {
        accepting = false;
        stopped = true;
        threads.forEach(Thread::interrupt);
    }

    /**
     * @return the number of items waiting in the queues, not counting the batches the handlers are working on
     */
    public int queued() {
        int total = 0;
        for (BlockingQueue<T> queue : queues) {
            total += queue.size();
        }
        return total;
    }

    private void requireAccepting() {
        if (!accepting) {
            throw new PipelineClosedException(threadNamePrefix + " workers no longer accept items");
        }
    }

    private void run(BlockingQueue<T> queue) {
        while (!Thread.currentThread().isInterrupted()) {
            List<T> batch = new ArrayList<>();
            try {
                batch.add(queue.take());
                queue.drainTo(batch, maxBatch - 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            int size = batch.size();
            try {
                batchHandler.accept(batch);
            } catch (Throwable t) {
                // an Error too: a dead worker would never serve its partition again
                log.error("[{}] Batch of {} items failed", Thread.currentThread().getName(), size, t);
            } finally {
                finished(size);
            }
        }
    }

    private void finished(int count) {
        if (unfinished.addAndGet(-count) == 0) {
            synchronized (idleMonitor) {
                idleMonitor.notifyAll();
            }
        }
    }

}
