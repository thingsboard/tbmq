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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.thingsboard.mqtt.broker.exception.PipelineClosedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class PartitionedWorkersTest {

    private static final long WAIT_SECONDS = 10;

    private PartitionedWorkers<Item> workers;
    private ExecutorService submitter;

    record Item(String key, int seq) {
    }

    @AfterEach
    void tearDown() {
        if (workers != null) {
            workers.stop();
        }
        if (submitter != null) {
            submitter.shutdownNow();
        }
    }

    @Test
    void givenManyItemsOverFiftyKeys_whenSubmit_thenPerKeyOrderIsKept() throws Exception {
        Map<String, List<Integer>> seen = new ConcurrentHashMap<>();
        workers = new PartitionedWorkers<>("test-order", 4, 100, 32);
        workers.start(batch ->
                batch.forEach(item -> seen.computeIfAbsent(item.key(), k -> new ArrayList<>()).add(item.seq())));

        int total = 10_000;
        Map<String, Integer> nextSeq = new HashMap<>();
        for (int i = 0; i < total; i++) {
            String key = "key-" + (i % 50);
            workers.submit(key, new Item(key, nextSeq.merge(key, 1, Integer::sum) - 1));
        }
        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();

        assertThat(seen).hasSize(50);
        seen.forEach((key, seqs) -> {
            assertThat(seqs).hasSize(total / 50);
            for (int i = 0; i < seqs.size(); i++) {
                assertThat(seqs.get(i)).as("order of %s", key).isEqualTo(i);
            }
        });
    }

    @Test
    void givenSameKey_whenSubmitRepeatedly_thenAlwaysSameThread() throws Exception {
        Map<String, Set<String>> threadsByKey = new ConcurrentHashMap<>();
        workers = new PartitionedWorkers<>("test-thread", 4, 0, 16);
        workers.start(batch ->
                batch.forEach(item -> threadsByKey.computeIfAbsent(item.key(), k -> ConcurrentHashMap.newKeySet())
                        .add(Thread.currentThread().getName())));

        for (int i = 0; i < 2_000; i++) {
            String key = "key-" + (i % 20);
            workers.submit(key, new Item(key, i));
        }
        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();

        assertThat(threadsByKey).hasSize(20);
        threadsByKey.values().forEach(threads -> assertThat(threads).hasSize(1));
        Set<String> allThreads = new HashSet<>();
        threadsByKey.values().forEach(allThreads::addAll);
        assertThat(allThreads).allMatch(name -> name.startsWith("test-thread-"));
    }

    @Test
    void givenBacklog_whenWorkerTakesBatches_thenNoBatchExceedsMaxBatch() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        workers = new PartitionedWorkers<>("test-batch", 1, 0, 10);
        workers.start(batch -> {
            awaitLatch(release);
            batchSizes.add(batch.size());
        });

        // the first item is taken at once; the other 95 pile up behind the blocked handler
        for (int i = 0; i < 96; i++) {
            workers.submit("k", new Item("k", i));
        }
        release.countDown();
        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();

        assertThat(batchSizes).allMatch(size -> size >= 1 && size <= 10);
        assertThat(batchSizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(96);
        assertThat(batchSizes).contains(10);
    }

    @Test
    void givenFullQueue_whenSubmit_thenBlocksUntilHandlerFreesSpace() throws Exception {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        workers = new PartitionedWorkers<>("test-full", 1, 2, 1);
        workers.start(batch -> {
            handlerEntered.countDown();
            awaitLatch(release);
        });
        workers.submit("k", new Item("k", 0));
        assertThat(handlerEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        workers.submit("k", new Item("k", 1));
        workers.submit("k", new Item("k", 2));
        assertThat(workers.queued()).isEqualTo(2);

        submitter = Executors.newSingleThreadExecutor();
        Future<?> blocked = submitter.submit(() -> {
            workers.submit("k", new Item("k", 3));
            return null;
        });

        assertThatThrownBy(() -> blocked.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
        assertThat(blocked.isDone()).isFalse();

        release.countDown();
        blocked.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();
    }

    @Test
    void givenHandlerBlocked_whenReadQueued_thenReadDoesNotWait() throws Exception {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        workers = new PartitionedWorkers<>("test-queued", 1, 0, 256);
        workers.start(batch -> {
            handlerEntered.countDown();
            awaitLatch(release);
        });
        workers.submit("k", new Item("k", 0));
        assertThat(handlerEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        for (int i = 1; i <= 5; i++) {
            workers.submit("k", new Item("k", i));
        }

        // the handler is still inside its call: the item it holds is not counted, the five behind it are
        assertThat(workers.queued()).isEqualTo(5);

        release.countDown();
        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();
        assertThat(workers.queued()).isZero();
    }

    @Test
    void givenEverythingHandled_whenDrain_thenReturnsTrue() throws Exception {
        AtomicInteger handled = new AtomicInteger();
        workers = new PartitionedWorkers<>("test-drain", 2, 0, 8);
        workers.start(batch -> handled.addAndGet(batch.size()));
        for (int i = 0; i < 500; i++) {
            workers.submit("key-" + i, new Item("key-" + i, i));
        }

        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();

        assertThat(handled).hasValue(500);
        assertThat(workers.queued()).isZero();
    }

    @Test
    void givenHandlerStillRunning_whenDrainTimesOut_thenReturnsFalse() throws Exception {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        workers = new PartitionedWorkers<>("test-timeout", 1, 0, 8);
        workers.start(batch -> {
            handlerEntered.countDown();
            awaitLatch(release);
        });
        workers.submit("k", new Item("k", 0));
        assertThat(handlerEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // the queue is empty, but the handler has not returned
        assertThat(workers.drain(100)).isFalse();

        release.countDown();
        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();
    }

    @Test
    void givenDrained_whenSubmit_thenThrowsPipelineClosedWithoutAStackTrace() throws Exception {
        workers = new PartitionedWorkers<>("test-after-drain", 1, 0, 8);
        workers.start(batch -> {
        });
        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();

        assertThatThrownBy(() -> workers.submit("k", new Item("k", 0)))
                .isInstanceOf(PipelineClosedException.class)
                .satisfies(e -> assertThat(e.getStackTrace()).isEmpty());
        assertThat(workers.queued()).isZero();
        // the rejected submit left nothing unfinished
        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();
    }

    @Test
    void givenThrowingHandler_whenBatchFails_thenWorkerContinuesAndDrainCompletes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Integer> handled = new CopyOnWriteArrayList<>();
        workers = new PartitionedWorkers<>("test-throw", 1, 0, 1);
        workers.start(batch -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("handler failure");
            }
            batch.forEach(item -> handled.add(item.seq()));
        });

        for (int i = 0; i < 5; i++) {
            workers.submit("k", new Item("k", i));
        }

        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();
        assertThat(handled).containsExactly(1, 2, 3, 4);
    }

    @Test
    void givenHandlerThrowingAnError_whenBatchFails_thenTheWorkerThreadSurvives() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PartitionedWorkers.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
        try {
            AtomicInteger calls = new AtomicInteger();
            List<Integer> handled = new CopyOnWriteArrayList<>();
            workers = new PartitionedWorkers<>("test-error", 1, 0, 1);
            workers.start(batch -> {
                if (calls.incrementAndGet() == 1) {
                    throw new StackOverflowError("handler overflow");
                }
                batch.forEach(item -> handled.add(item.seq()));
            });

            for (int i = 0; i < 5; i++) {
                workers.submit("k", new Item("k", i));
            }

            assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();
            assertThat(handled).containsExactly(1, 2, 3, 4);
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrowableProxy().getMessage()).isEqualTo("handler overflow");
            });
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(true);
            appender.stop();
        }
    }

    @Test
    void givenBlockedSubmitter_whenStop_thenSubmitFailsAndWorkersAreInterrupted() throws Exception {
        CountDownLatch handlerEntered = new CountDownLatch(1);
        AtomicInteger interrupted = new AtomicInteger();
        workers = new PartitionedWorkers<>("test-stop", 1, 1, 1);
        workers.start(batch -> {
            handlerEntered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.incrementAndGet();
                Thread.currentThread().interrupt();
            }
        });
        workers.submit("k", new Item("k", 0));
        assertThat(handlerEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        workers.submit("k", new Item("k", 1));
        submitter = Executors.newSingleThreadExecutor();
        Future<?> blocked = submitter.submit(() -> {
            workers.submit("k", new Item("k", 2));
            return null;
        });

        workers.stop();

        await().atMost(WAIT_SECONDS, TimeUnit.SECONDS).untilAsserted(() -> assertThat(interrupted).hasValue(1));
        assertThatThrownBy(() -> blocked.get(WAIT_SECONDS, TimeUnit.SECONDS)).hasRootCauseInstanceOf(PipelineClosedException.class);
        assertThatThrownBy(() -> workers.submit("k", new Item("k", 3))).isInstanceOf(PipelineClosedException.class);
    }

    @Test
    void givenInvalidArguments_whenCreate_thenRejected() {
        assertThatThrownBy(() -> new PartitionedWorkers<Item>("t", 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartitionedWorkers<Item>("t", 1, -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartitionedWorkers<Item>("t", 1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenItemsSubmittedBeforeStart_whenStart_thenTheHandlerGetsThemInOrder() throws Exception {
        List<Integer> handled = new CopyOnWriteArrayList<>();
        workers = new PartitionedWorkers<>("test-before-start", 1, 0, 4);
        for (int i = 0; i < 10; i++) {
            workers.submit("k", new Item("k", i));
        }
        assertThat(workers.queued()).isEqualTo(10);

        workers.start(batch -> batch.forEach(item -> handled.add(item.seq())));

        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();
        assertThat(handled).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    void givenStarted_whenStartAgain_thenRejected() {
        workers = new PartitionedWorkers<>("test-start-twice", 1, 0, 1);
        workers.start(batch -> {
        });

        assertThatThrownBy(() -> workers.start(batch -> {
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PartitionedWorkers<Item>("t", 1, 0, 1).start(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void givenHandlerPerPartition_whenItemsKeyedToEachPartition_thenEachHandlerSeesOnlyItsPartition() throws Exception {
        int partitions = 3;
        List<Integer> built = new CopyOnWriteArrayList<>();
        Map<Integer, List<String>> seenByPartition = new ConcurrentHashMap<>();
        workers = new PartitionedWorkers<>("test-handlers", partitions, 0, 8);
        workers.startPerPartition(partition -> {
            built.add(partition);
            return batch -> batch.forEach(item ->
                    seenByPartition.computeIfAbsent(partition, p -> new CopyOnWriteArrayList<>()).add(item.key()));
        });

        // submit() routes by the key's hash, so key a probe per partition the same way
        Map<Integer, String> keyOfPartition = new HashMap<>();
        for (int i = 0; keyOfPartition.size() < partitions; i++) {
            keyOfPartition.putIfAbsent(Math.floorMod(("probe-" + i).hashCode(), partitions), "probe-" + i);
        }
        for (int round = 0; round < 5; round++) {
            for (String key : keyOfPartition.values()) {
                workers.submit(key, new Item(key, round));
            }
        }
        assertThat(workers.drain(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))).isTrue();

        assertThat(built).containsExactlyInAnyOrder(0, 1, 2);
        keyOfPartition.forEach((partition, key) ->
                assertThat(seenByPartition.get(partition)).as("items of partition %s", partition).containsOnly(key).hasSize(5));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
