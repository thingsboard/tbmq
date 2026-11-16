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
import org.mockito.ArgumentCaptor;
import org.thingsboard.mqtt.broker.common.stats.StatsFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PipelineBudgetTest {

    private static final long UNLIMITED_BYTES = Long.MAX_VALUE / 200;

    @Test
    void givenMessageLimit_whenAcquireUpToLimit_thenNextIsRefused() {
        PipelineBudget budget = new PipelineBudget(3, UNLIMITED_BYTES, 80, 50);

        assertThat(budget.tryAcquire(1)).isTrue();
        assertThat(budget.tryAcquire(1)).isTrue();
        assertThat(budget.tryAcquire(1)).isTrue();
        assertThat(budget.tryAcquire(1)).isFalse();

        assertThat(budget.queuedMessages()).isEqualTo(3);
        assertThat(budget.queuedBytes()).isEqualTo(3);
    }

    @Test
    void givenMessageLimit_whenRelease_thenSlotIsFreeAgain() {
        PipelineBudget budget = new PipelineBudget(1, UNLIMITED_BYTES, 80, 50);
        assertThat(budget.tryAcquire(10)).isTrue();
        assertThat(budget.tryAcquire(10)).isFalse();

        budget.release(10);

        assertThat(budget.tryAcquire(10)).isTrue();
    }

    @Test
    void givenByteLimit_whenBytesWouldExceedIt_thenRefusedByBytesAlone() {
        PipelineBudget budget = new PipelineBudget(1000, 100, 80, 50);

        assertThat(budget.tryAcquire(60)).isTrue();
        assertThat(budget.tryAcquire(41)).isFalse();
        assertThat(budget.queuedMessages()).isEqualTo(1);
        assertThat(budget.queuedBytes()).isEqualTo(60);
        assertThat(budget.tryAcquire(40)).isTrue();
        assertThat(budget.tryAcquire(1)).isFalse();
    }

    @Test
    void givenEmptyPipeline_whenMessageLargerThanByteLimit_thenAdmittedAlone() {
        PipelineBudget budget = new PipelineBudget(1000, 100, 80, 50);

        assertThat(budget.tryAcquire(500)).isTrue();
        assertThat(budget.tryAcquire(1)).isFalse();

        budget.release(500);

        assertThat(budget.tryAcquire(1)).isTrue();
    }

    @Test
    void givenMessagesBelowPause_whenReachPause_thenAbovePause() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);

        for (int i = 0; i < 7; i++) {
            budget.tryAcquire(1);
        }
        assertThat(budget.abovePause()).isFalse();

        budget.tryAcquire(1);

        assertThat(budget.abovePause()).isTrue();
    }

    @Test
    void givenBytesReachPause_whenCheck_thenAbovePause() {
        PipelineBudget budget = new PipelineBudget(1000, 100, 80, 50);

        budget.tryAcquire(79);
        assertThat(budget.abovePause()).isFalse();

        budget.tryAcquire(1);

        assertThat(budget.abovePause()).isTrue();
    }

    @Test
    void givenMaxBytesOfLongMaxValue_whenEmpty_thenNotAbovePauseAndNoProductOverflows() {
        PipelineBudget budget = new PipelineBudget(10, Long.MAX_VALUE, 80, 50);

        assertThat(budget.abovePause()).isFalse();
        assertThat(budget.hasRoom(Integer.MAX_VALUE)).isTrue();
        assertThat(budget.tryAcquire(Integer.MAX_VALUE)).isTrue();
        assertThat(budget.abovePause()).isFalse();
    }

    @Test
    void givenMaxBytesOfLongMaxValue_whenMessagesReachPause_thenPauseAndResumeFireAtTheirPercentages() {
        PipelineBudget budget = new PipelineBudget(10, Long.MAX_VALUE, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        budget.onResume(resumed::incrementAndGet);

        for (int i = 0; i < 7; i++) {
            budget.tryAcquire(1);
        }
        assertThat(budget.abovePause()).isFalse();
        budget.tryAcquire(1);
        assertThat(budget.abovePause()).isTrue();

        budget.release(1);
        budget.release(1);
        assertThat(resumed).hasValue(0);
        budget.release(1);

        assertThat(budget.queuedMessages()).isEqualTo(5);
        assertThat(resumed).hasValue(1);
    }

    @Test
    void givenMaxBytesOfLongMaxValue_whenForcedTakesPushBytesPastTheMaximum_thenStillPausedAndResumesWhenReleased() {
        PipelineBudget budget = new PipelineBudget(Integer.MAX_VALUE, Long.MAX_VALUE, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        budget.onResume(resumed::incrementAndGet);

        // forced takes go past the byte maximum: the bound must leave room for queuedBytes * 100 beyond it
        int taken = 0;
        while (budget.queuedBytes() <= Long.MAX_VALUE / 200) {
            budget.acquire(Integer.MAX_VALUE);
            taken++;
        }
        budget.acquire(Integer.MAX_VALUE);
        taken++;

        assertThat(budget.queuedBytes()).isGreaterThan(Long.MAX_VALUE / 200).isPositive();
        assertThat(budget.abovePause()).isTrue();
        assertThat(resumed).hasValue(0);

        for (int i = 0; i < taken; i++) {
            budget.release(Integer.MAX_VALUE);
        }

        assertThat(budget.queuedBytes()).isZero();
        assertThat(budget.abovePause()).isFalse();
        assertThat(resumed).hasValue(1);
    }

    @Test
    void givenAbovePause_whenUsageFallsToResume_thenListenerFiresOnce() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        budget.onResume(resumed::incrementAndGet);
        for (int i = 0; i < 8; i++) {
            budget.tryAcquire(1);
        }

        budget.release(1);
        budget.release(1);
        assertThat(resumed).hasValue(0);
        assertThat(budget.queuedMessages()).isEqualTo(6);

        budget.release(1);
        assertThat(budget.queuedMessages()).isEqualTo(5);
        assertThat(resumed).hasValue(1);

        budget.release(1);
        budget.release(1);

        assertThat(resumed).hasValue(1);
    }

    @Test
    void givenResumingStopped_whenUsageFallsToResume_thenNoListenerRuns() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        budget.onResume(resumed::incrementAndGet);
        for (int i = 0; i < 8; i++) {
            budget.tryAcquire(1);
        }

        budget.stopResuming();
        for (int i = 0; i < 8; i++) {
            budget.release(1);
        }

        assertThat(budget.queuedMessages()).isZero();
        assertThat(resumed).hasValue(0);
    }

    @Test
    void givenAListenerRunning_whenStopResuming_thenItWaitsForTheListenerToReturn() throws Exception {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        CountDownLatch listenerEntered = new CountDownLatch(1);
        CountDownLatch releaseListener = new CountDownLatch(1);
        budget.onResume(() -> {
            listenerEntered.countDown();
            try {
                releaseListener.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        for (int i = 0; i < 8; i++) {
            budget.tryAcquire(1);
        }
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            threads.submit(() -> {
                for (int i = 0; i < 3; i++) {
                    budget.release(1);
                }
            });
            assertThat(listenerEntered.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> stop = threads.submit(budget::stopResuming);

            // a listener that already started may still resume reading: the shutdown must pause reading after it
            assertThatThrownBy(() -> stop.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseListener.countDown();
            stop.get(10, TimeUnit.SECONDS);
        } finally {
            releaseListener.countDown();
            threads.shutdownNow();
        }
    }

    @Test
    void givenUsageStillPaused_whenResumeUnlessPaused_thenItDoesNotRun() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        for (int i = 0; i < 8; i++) {
            budget.tryAcquire(1);
        }
        // below the pause level, but not yet down to the resume level
        budget.release(1);
        budget.release(1);

        budget.resumeUnlessPaused(resumed::incrementAndGet);

        assertThat(resumed).hasValue(0);
    }

    @Test
    void givenNeverPausedOrResumedSince_whenResumeUnlessPaused_thenItRuns() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger resumed = new AtomicInteger();

        budget.resumeUnlessPaused(resumed::incrementAndGet);
        for (int i = 0; i < 8; i++) {
            budget.tryAcquire(1);
        }
        for (int i = 0; i < 3; i++) {
            budget.release(1);
        }
        budget.resumeUnlessPaused(resumed::incrementAndGet);

        assertThat(resumed).hasValue(2);
    }

    @Test
    void givenResumingStopped_whenResumeUnlessPaused_thenItDoesNotRun() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger resumed = new AtomicInteger();

        budget.stopResuming();
        budget.resumeUnlessPaused(resumed::incrementAndGet);

        assertThat(resumed).hasValue(0);
    }

    @Test
    void givenAResumeUnlessPausedRunning_whenStopResuming_thenItWaitsForTheResumeToReturn() throws Exception {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        CountDownLatch resumeEntered = new CountDownLatch(1);
        CountDownLatch releaseResume = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            threads.submit(() -> budget.resumeUnlessPaused(() -> {
                resumeEntered.countDown();
                try {
                    releaseResume.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(resumeEntered.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> stop = threads.submit(budget::stopResuming);

            assertThatThrownBy(() -> stop.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseResume.countDown();
            stop.get(10, TimeUnit.SECONDS);
        } finally {
            releaseResume.countDown();
            threads.shutdownNow();
        }
    }

    @Test
    void givenRoomOrNot_whenHasRoom_thenItAnswersLikeTryAcquireWithoutTakingAnything() {
        PipelineBudget budget = new PipelineBudget(2, 10, 80, 50);
        assertThat(budget.hasRoom(50)).as("an empty pipeline admits one oversized message").isTrue();
        budget.tryAcquire(5);

        assertThat(budget.hasRoom(5)).isTrue();
        assertThat(budget.hasRoom(6)).isFalse();
        budget.tryAcquire(5);
        assertThat(budget.hasRoom(0)).isFalse();
        assertThat(budget.queuedMessages()).isEqualTo(2);
        assertThat(budget.queuedBytes()).isEqualTo(10);
    }

    @Test
    void givenFullBudget_whenAcquire_thenTakenOverTheLimit() {
        PipelineBudget budget = new PipelineBudget(2, 10, 80, 50);
        budget.tryAcquire(5);
        budget.tryAcquire(5);
        assertThat(budget.tryAcquire(1)).isFalse();

        budget.acquire(7);

        assertThat(budget.queuedMessages()).isEqualTo(3);
        assertThat(budget.queuedBytes()).isEqualTo(17);
        assertThat(budget.abovePause()).isTrue();
    }

    @Test
    void givenAbovePauseOnBytesOnly_whenBytesFallToResume_thenListenerFires() {
        PipelineBudget budget = new PipelineBudget(1000, 100, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        budget.onResume(resumed::incrementAndGet);
        budget.tryAcquire(10);
        budget.tryAcquire(80);

        budget.release(80);

        assertThat(resumed).hasValue(1);
    }

    @Test
    void givenNeverReachedPause_whenUsageFalls_thenListenerDoesNotFire() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        budget.onResume(resumed::incrementAndGet);
        for (int i = 0; i < 7; i++) {
            budget.tryAcquire(1);
        }

        for (int i = 0; i < 7; i++) {
            budget.release(1);
        }

        assertThat(resumed).hasValue(0);
    }

    @Test
    void givenResumed_whenPauseReachedAgain_thenListenerFiresAgain() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        budget.onResume(resumed::incrementAndGet);

        for (int cycle = 1; cycle <= 2; cycle++) {
            for (int i = 0; i < 8; i++) {
                budget.tryAcquire(1);
            }
            for (int i = 0; i < 8; i++) {
                budget.release(1);
            }
            assertThat(resumed).hasValue(cycle);
        }
    }

    @Test
    void givenThrowingListener_whenResume_thenOthersStillRun() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        budget.onResume(() -> {
            throw new IllegalStateException("listener failure");
        });
        budget.onResume(resumed::incrementAndGet);
        for (int i = 0; i < 8; i++) {
            budget.tryAcquire(1);
        }

        for (int i = 0; i < 8; i++) {
            budget.release(1);
        }

        assertThat(resumed).hasValue(1);
    }

    @Test
    void givenListenerThrowingAnError_whenResume_thenOthersStillRunAndTheReleaseReturns() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger resumed = new AtomicInteger();
        budget.onResume(() -> {
            throw new StackOverflowError("listener overflow");
        });
        budget.onResume(resumed::incrementAndGet);
        for (int i = 0; i < 8; i++) {
            budget.tryAcquire(1);
        }

        for (int i = 0; i < 8; i++) {
            budget.release(1);
        }

        assertThat(resumed).hasValue(1);
        assertThat(budget.queuedMessages()).isZero();
    }

    @Test
    void givenNothingAcquired_whenRelease_thenCountersStayAtZero() {
        PipelineBudget budget = new PipelineBudget(10, 100, 80, 50);

        budget.release(50);
        assertThat(budget.queuedMessages()).isZero();
        assertThat(budget.queuedBytes()).isZero();

        budget.tryAcquire(10);
        budget.release(500);
        budget.release(1);

        assertThat(budget.queuedMessages()).isZero();
        assertThat(budget.queuedBytes()).isZero();
    }

    @Test
    void givenNegativeBytes_whenAcquireOrRelease_thenRejected() {
        PipelineBudget budget = new PipelineBudget(10, 100, 80, 50);

        assertThatThrownBy(() -> budget.tryAcquire(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> budget.release(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(budget.queuedMessages()).isZero();
    }

    @Test
    void givenInvalidSettings_whenCreate_thenRejected() {
        assertThatThrownBy(() -> new PipelineBudget(0, 100, 80, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PipelineBudget(10, 0, 80, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PipelineBudget(10, 100, 101, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PipelineBudget(10, 100, 80, 80)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PipelineBudget(10, 100, 80, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenResumingNotStopped_whenRunUnlessResumingStopped_thenTheActionRuns() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger ran = new AtomicInteger();

        assertThat(budget.resumingStopped()).isFalse();
        assertThat(budget.runUnlessResumingStopped(ran::incrementAndGet)).isTrue();

        assertThat(ran).hasValue(1);
    }

    @Test
    void givenResumingStopped_whenRunUnlessResumingStopped_thenTheActionDoesNotRun() {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        AtomicInteger ran = new AtomicInteger();

        budget.stopResuming();

        assertThat(budget.resumingStopped()).isTrue();
        assertThat(budget.runUnlessResumingStopped(ran::incrementAndGet)).isFalse();
        assertThat(ran).hasValue(0);
    }

    @Test
    void givenListenerBlockedOnReleasingThread_whenReadingDepth_thenReadsDoNotWait() throws Exception {
        PipelineBudget budget = new PipelineBudget(10, UNLIMITED_BYTES, 80, 50);
        CountDownLatch listenerEntered = new CountDownLatch(1);
        CountDownLatch finishListener = new CountDownLatch(1);
        budget.onResume(() -> {
            listenerEntered.countDown();
            try {
                finishListener.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        for (int i = 0; i < 8; i++) {
            budget.tryAcquire(2);
        }
        Thread releaser = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 8; i++) {
                budget.release(2);
            }
        });
        try {
            assertThat(listenerEntered.await(10, TimeUnit.SECONDS)).isTrue();

            // the releasing thread is parked inside the listener; neither the reads nor a new acquire wait for it
            assertThat(budget.queuedMessages()).isEqualTo(5);
            assertThat(budget.queuedBytes()).isEqualTo(10);
            assertThat(budget.abovePause()).isFalse();
            assertThat(budget.tryAcquire(2)).isTrue();
        } finally {
            finishListener.countDown();
            releaser.join(10_000);
        }
    }

    @Test
    void givenStatsFactory_whenCreateFromProperties_thenGaugesReadTheCounters() {
        StatsFactory statsFactory = mock(StatsFactory.class);
        PipelineProperties properties = new PipelineProperties();
        properties.getPipeline().setMaxBytes(1000);

        PipelineBudget budget = new PipelineBudget(properties, statsFactory);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ToDoubleFunction<PipelineBudget>> messagesGauge = ArgumentCaptor.forClass(ToDoubleFunction.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ToDoubleFunction<PipelineBudget>> bytesGauge = ArgumentCaptor.forClass(ToDoubleFunction.class);
        verify(statsFactory).createGauge(eq("standalonePipelineQueuedMessages"), eq(budget), messagesGauge.capture());
        verify(statsFactory).createGauge(eq("standalonePipelineQueuedBytes"), eq(budget), bytesGauge.capture());
        budget.tryAcquire(300);
        budget.tryAcquire(200);
        assertThat(messagesGauge.getValue().applyAsDouble(budget)).isEqualTo(2.0);
        assertThat(bytesGauge.getValue().applyAsDouble(budget)).isEqualTo(500.0);
    }

    @Test
    void givenDefaultProperties_whenCreateFromProperties_thenLimitsAreTheDefaults() {
        PipelineBudget budget = new PipelineBudget(new PipelineProperties(), mock(StatsFactory.class));

        int admitted = 0;
        while (budget.tryAcquire(0)) {
            admitted++;
        }
        assertThat(admitted).isEqualTo(100_000);
    }

}
