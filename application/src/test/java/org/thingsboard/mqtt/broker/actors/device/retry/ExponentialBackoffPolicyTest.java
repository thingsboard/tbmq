/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.actors.device.retry;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willAnswer;
import static org.thingsboard.mqtt.broker.actors.device.retry.ExponentialBackoffPolicy.EXP_MAX;
import static org.thingsboard.mqtt.broker.actors.device.retry.ExponentialBackoffPolicy.JITTER_MAX;

@Slf4j
class ExponentialBackoffPolicyTest {

    @Execution(ExecutionMode.SAME_THREAD) // just for convenient log reading
    @ParameterizedTest
    @ValueSource(ints = {1, 0, 60})
    public void exponentialReconnectDelayTest(final int reconnectIntervalMinSeconds) {
        final ExponentialBackoffPolicy strategy = Mockito.spy(new ExponentialBackoffPolicy(reconnectIntervalMinSeconds));
        log.warn("=== Reconnect delay test for ReconnectStrategyExponential({}) : calculated min [{}] max [{}] ===", reconnectIntervalMinSeconds, strategy.getRetryIntervalMinSeconds(), strategy.getRetryIntervalMaxSeconds());
        final AtomicLong nanoTime = new AtomicLong(System.nanoTime());
        willAnswer((x) -> nanoTime.get()).given(strategy).getNanoTime();
        final LinkedBlockingDeque<Long> jittersCaptured = new LinkedBlockingDeque<>();
        final LinkedBlockingDeque<Long> expCaptured = new LinkedBlockingDeque<>();

        willAnswer(captureResult(jittersCaptured)).given(strategy).calculateJitter();
        willAnswer(captureResult(expCaptured)).given(strategy).calculateExp(anyLong());

        for (int phase = 0; phase < 3; phase++) {
            log.warn("== Phase {} ==", phase);
            long previousDelay = 0;
            for (int i = 0; i < EXP_MAX + 4; i++) {
                final long nextReconnectDelay = strategy.nextDelay();
                nanoTime.addAndGet(TimeUnit.SECONDS.toNanos(nextReconnectDelay));
                log.warn("Retry [{}] Delay [{}] : min [{}] exp [{}] jitter [{}]", strategy.getRetryCount(), nextReconnectDelay, strategy.getRetryIntervalMinSeconds(), expCaptured.peekLast(), jittersCaptured.peekLast());
                assertThat(previousDelay).satisfiesAnyOf(
                        v -> assertThat(v).isLessThanOrEqualTo(nextReconnectDelay),
                        v -> assertThat(v).isCloseTo(nextReconnectDelay, offset(JITTER_MAX)) // Adjust tolerance as needed
                );
                previousDelay = nextReconnectDelay;
            }
            log.warn("Jitters captured: {}", drainAll(jittersCaptured));
            log.warn("Exponents captured: {}", drainAll(expCaptured));
            assertThat(previousDelay).isCloseTo(strategy.getRetryIntervalMaxSeconds(), offset(JITTER_MAX));

            final long coolDownPeriodSec = strategy.getRetryIntervalMinSeconds() + strategy.getRetryIntervalMaxSeconds() + 1;
            log.warn("Cooling down for [{}] seconds ...", coolDownPeriodSec);
            nanoTime.addAndGet(TimeUnit.SECONDS.toNanos(coolDownPeriodSec));
            assertThat(strategy.isCooledDown(TimeUnit.SECONDS.toNanos(coolDownPeriodSec))).as("cooled down").isTrue();
        }
    }

    private Answer<Long> captureResult(Collection<Long> collection) {
        return invocation -> {
            long result = (long) invocation.callRealMethod();
            collection.add(result);
            return result;
        };
    }

    private Collection<Long> drainAll(BlockingQueue<Long> jittersCaptured) {
        Collection<Long> elements = new ArrayList<>();
        jittersCaptured.drainTo(elements);
        return elements;
    }

}
