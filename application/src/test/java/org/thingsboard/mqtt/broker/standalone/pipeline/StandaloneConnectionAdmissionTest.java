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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StandaloneConnectionAdmissionTest {

    private final StandaloneConnectionAdmission admission = new StandaloneConnectionAdmission();

    @Test
    void givenOpen_whenTryAdmit_thenItIsAdmittedAndInFlightUntilReleased() {
        UUID sessionId = UUID.randomUUID();

        assertThat(admission.tryAdmit(sessionId)).isTrue();
        assertThat(admission.inFlight()).isEqualTo(1);

        admission.release(sessionId);

        assertThat(admission.inFlight()).isZero();
    }

    @Test
    void givenAnIdNeverAdmitted_whenRelease_thenInFlightIsUnchanged() {
        admission.tryAdmit(UUID.randomUUID());

        admission.release(UUID.randomUUID());

        assertThat(admission.inFlight()).isEqualTo(1);
    }

    @Test
    void givenClosed_whenTryAdmit_thenItIsRefusedAndNotTracked() {
        admission.close();

        assertThat(admission.tryAdmit(UUID.randomUUID())).isFalse();
        assertThat(admission.inFlight()).isZero();
    }

    @Test
    void givenOneAdmittedBeforeTheClose_whenClosed_thenItStaysInFlightUntilReleased() {
        UUID sessionId = UUID.randomUUID();
        admission.tryAdmit(sessionId);

        admission.close();

        assertThat(admission.inFlight()).isEqualTo(1);
        admission.release(sessionId);
        assertThat(admission.inFlight()).isZero();
    }

    @Test
    void givenAdmissionsRacingTheClose_whenClosed_thenEveryAdmittedOneIsTrackedAndNoneAfter() throws Exception {
        int threads = 4;
        AtomicBoolean closeReturned = new AtomicBoolean();
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger admittedAfterClose = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> loops = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                loops.add(executor.submit(() -> {
                    started.countDown();
                    int callsAfterClose = 0;
                    while (callsAfterClose < 1_000) {
                        boolean afterClose = closeReturned.get();
                        if (admission.tryAdmit(UUID.randomUUID())) {
                            admitted.incrementAndGet();
                            if (afterClose) {
                                admittedAfterClose.incrementAndGet();
                            }
                        }
                        if (afterClose) {
                            callsAfterClose++;
                        }
                    }
                }));
            }
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            await().atMost(10, TimeUnit.SECONDS).until(() -> admitted.get() >= 1_000);

            admission.close();
            // once close() returned, no CONNECT is between the check and being tracked: nothing joins those in flight
            int inFlightWhenClosed = admission.inFlight();
            closeReturned.set(true);
            for (Future<?> loop : loops) {
                loop.get(10, TimeUnit.SECONDS);
            }

            assertThat(admittedAfterClose).hasValue(0);
            assertThat(admission.inFlight()).isEqualTo(inFlightWhenClosed).isEqualTo(admitted.get());
        } finally {
            executor.shutdownNow();
        }
    }

}
