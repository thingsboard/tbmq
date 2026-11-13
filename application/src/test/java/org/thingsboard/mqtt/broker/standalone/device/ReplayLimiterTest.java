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
package org.thingsboard.mqtt.broker.standalone.device;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayLimiterTest {

    private static final long STALL_TIMEOUT_MS = 30_000;

    private final TestClock clock = new TestClock();
    private final ReplayLimiter limiter = new ReplayLimiter(16, STALL_TIMEOUT_MS, clock);

    @Test
    void the17thClientWaitsForAPermit() {
        IntStream.range(0, 16).forEach(i -> assertThat(limiter.tryAcquire("c" + i)).isTrue());

        assertThat(limiter.tryAcquire("c16")).isFalse();
    }

    @Test
    void aReleasedPermitGoesToTheNextClient() {
        IntStream.range(0, 16).forEach(i -> limiter.tryAcquire("c" + i));

        limiter.release("c3");

        assertThat(limiter.tryAcquire("c16")).isTrue();
        assertThat(limiter.tryAcquire("c17")).isFalse();
    }

    @Test
    void aHolderAcquiresAgainWithoutTakingASecondPermit() {
        IntStream.range(0, 15).forEach(i -> limiter.tryAcquire("c" + i));

        assertThat(limiter.tryAcquire("c0")).isTrue();
        assertThat(limiter.tryAcquire("c15")).isTrue();
        assertThat(limiter.tryAcquire("c16")).isFalse();
    }

    @Test
    void releasingANonHolderChangesNothing() {
        IntStream.range(0, 16).forEach(i -> limiter.tryAcquire("c" + i));

        limiter.release("other");
        limiter.release("c0");
        limiter.release("c0");

        assertThat(limiter.tryAcquire("c16")).isTrue();
        assertThat(limiter.tryAcquire("c17")).isFalse();
    }

    @Test
    void aHolderThatMadeNoProgressForTheStallTimeoutLosesItsPermitToAWaitingClient() {
        givenSixteenHoldersWithC0TheOldest();
        clock.advance(STALL_TIMEOUT_MS - 2);
        assertThat(limiter.tryAcquire("c16")).isFalse();

        clock.advance(1);

        assertThat(limiter.tryAcquire("c16")).isTrue();
        assertThat(limiter.renew("c0")).isFalse();                                // one stalled holder gave up its permit
        assertThat(limiter.renew("c1")).isTrue();
    }

    @Test
    void aHolderThatRenewsKeepsItsPermit() {
        IntStream.range(0, 16).forEach(i -> limiter.tryAcquire("c" + i));
        clock.advance(STALL_TIMEOUT_MS - 1);
        IntStream.range(0, 16).forEach(i -> limiter.renew("c" + i));

        clock.advance(STALL_TIMEOUT_MS - 1);
        assertThat(limiter.tryAcquire("c16")).isFalse();
        clock.advance(1);
        assertThat(limiter.tryAcquire("c16")).isTrue();
    }

    @Test
    void theHolderStalledLongestLosesItsPermitFirst() {
        IntStream.range(0, 16).forEach(i -> {
            limiter.tryAcquire("c" + i);
            clock.advance(1);
        });
        limiter.renew("c0");
        clock.advance(STALL_TIMEOUT_MS);

        assertThat(limiter.tryAcquire("c16")).isTrue();
        assertThat(limiter.renew("c0")).isTrue();
        assertThat(limiter.renew("c1")).isFalse();
    }

    @Test
    void aClientThatLostItsPermitWaitsLikeAnyOther() {
        givenSixteenHoldersWithC0TheOldest();
        clock.advance(STALL_TIMEOUT_MS - 1);
        assertThat(limiter.tryAcquire("c16")).isTrue();
        IntStream.range(1, 17).forEach(i -> limiter.renew("c" + i));

        assertThat(limiter.renew("c0")).isFalse();
        assertThat(limiter.tryAcquire("c0")).isFalse();
    }

    @Test
    void aLimitBelowOneIsRejected() {
        assertThatThrownBy(() -> new ReplayLimiter(0, STALL_TIMEOUT_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standalone.persist.max-concurrent-replays");
    }

    @Test
    void aStallTimeoutBelowOneIsRejected() {
        assertThatThrownBy(() -> new ReplayLimiter(16, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standalone.persist.replay-stall-timeout-ms");
    }

    private void givenSixteenHoldersWithC0TheOldest() {
        limiter.tryAcquire("c0");
        clock.advance(1);
        IntStream.range(1, 16).forEach(i -> limiter.tryAcquire("c" + i));
    }

    private static final class TestClock extends Clock {

        private long millis = 1_700_000_000_000L;

        void advance(long ms) {
            millis += ms;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

    }

}
