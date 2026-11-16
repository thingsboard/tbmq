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

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.processing.MultiplePublishMsgCallbackWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CompleteOncePublishCallbackTest {

    private static final long UNLIMITED_BYTES = Long.MAX_VALUE / 200;

    private final PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
    private final TbQueueCallback queueCallback = mock(TbQueueCallback.class);

    @Test
    void oneFailingCopyAmongManyGivesExactlyOneNegativeAcknowledgement() {
        assertThat(budget.tryAcquire(100)).isTrue();
        var cb = new CompleteOncePublishCallback(budget, 100, queueCallback);
        var wrapper = new MultiplePublishMsgCallbackWrapper(300, cb);

        for (int i = 0; i < 150; i++) {
            wrapper.onSuccess();
        }
        wrapper.onFailure(new StoreException("x"));
        wrapper.onFailure(new StoreException("y"));
        for (int i = 0; i < 148; i++) {
            wrapper.onSuccess();
        }

        verify(queueCallback, times(1)).onFailure(any());
        verify(queueCallback, never()).onSuccess(any());
        assertThat(budget.queuedMessages()).isZero();
        assertThat(budget.queuedBytes()).isZero();
    }

    @Test
    void givenSucceeded_whenCompletedAgain_thenLaterCallsAreIgnored() {
        budget.tryAcquire(100);
        budget.tryAcquire(100);
        var cb = new CompleteOncePublishCallback(budget, 100, queueCallback);

        cb.onSuccess();
        cb.onSuccess();
        cb.onFailure(new StoreException("late"));

        verify(queueCallback, times(1)).onSuccess(null);
        verify(queueCallback, never()).onFailure(any());
        // released once: the other publish keeps its share
        assertThat(budget.queuedMessages()).isEqualTo(1);
        assertThat(budget.queuedBytes()).isEqualTo(100);
    }

    @Test
    void givenFailed_whenSucceedsLater_thenOnlyTheFailureIsReported() {
        budget.tryAcquire(100);
        budget.tryAcquire(100);
        var cb = new CompleteOncePublishCallback(budget, 100, queueCallback);
        StoreException failure = new StoreException("disk");

        cb.onFailure(failure);
        cb.onSuccess();
        cb.onBatchSuccess(3);

        verify(queueCallback, times(1)).onFailure(failure);
        verify(queueCallback, never()).onSuccess(any());
        assertThat(budget.queuedMessages()).isEqualTo(1);
        assertThat(budget.queuedBytes()).isEqualTo(100);
    }

    @Test
    void givenAReleaseThatThrowsAnError_whenTheCopiesComplete_thenThePublisherIsStillAnsweredOnce() {
        PipelineBudget failingBudget = mock(PipelineBudget.class);
        doThrow(new Error("resume listener")).when(failingBudget).release(100);
        var succeeding = new CompleteOncePublishCallback(failingBudget, 100, queueCallback);
        TbQueueCallback failedCallback = mock(TbQueueCallback.class);
        var failing = new CompleteOncePublishCallback(failingBudget, 100, failedCallback);
        StoreException failure = new StoreException("disk");

        assertThatThrownBy(succeeding::onSuccess).hasMessage("resume listener");
        assertThatThrownBy(() -> failing.onFailure(failure)).hasMessage("resume listener");
        succeeding.onSuccess();
        failing.onFailure(failure);

        // an unanswered publish would hold every later PUBACK of its client back for good
        verify(queueCallback, times(1)).onSuccess(null);
        verify(failedCallback, times(1)).onFailure(failure);
        verify(failingBudget, times(2)).release(100);
    }

    @Test
    void givenAPublisherCallbackThatThrows_whenCompleted_thenTheShareIsStillReleasedOnce() {
        budget.tryAcquire(100);
        budget.tryAcquire(100);
        doThrow(new IllegalStateException("callback")).when(queueCallback).onSuccess(any());
        var cb = new CompleteOncePublishCallback(budget, 100, queueCallback);

        assertThatThrownBy(cb::onSuccess).hasMessage("callback");
        cb.onFailure(new StoreException("late"));

        verify(queueCallback, times(1)).onSuccess(null);
        verify(queueCallback, never()).onFailure(any());
        assertThat(budget.queuedMessages()).isEqualTo(1);
        assertThat(budget.queuedBytes()).isEqualTo(100);
    }

    @Test
    void givenCopiesCompletingOnManyThreads_whenRacing_thenThePublishCompletesOnce() throws Exception {
        int threads = 8;
        int rounds = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                PipelineBudget roundBudget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
                roundBudget.tryAcquire(10);
                roundBudget.tryAcquire(10);
                AtomicInteger completions = new AtomicInteger();
                TbQueueCallback roundCallback = mock(TbQueueCallback.class);
                doAnswer(inv -> completions.incrementAndGet()).when(roundCallback).onSuccess(any());
                doAnswer(inv -> completions.incrementAndGet()).when(roundCallback).onFailure(any());
                var cb = new CompleteOncePublishCallback(roundBudget, 10, roundCallback);

                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    boolean fail = t % 2 == 0;
                    futures.add(executor.submit(() -> {
                        start.await();
                        if (fail) {
                            cb.onFailure(new StoreException("copy failed"));
                        } else {
                            cb.onSuccess();
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }

                assertThat(completions).as("completions in round %s", round).hasValue(1);
                // released once: the other publish keeps its share
                assertThat(roundBudget.queuedMessages()).isEqualTo(1);
                assertThat(roundBudget.queuedBytes()).isEqualTo(10);
            }
        } finally {
            executor.shutdownNow();
        }
    }

}
