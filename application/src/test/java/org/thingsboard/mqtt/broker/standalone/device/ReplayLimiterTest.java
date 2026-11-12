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

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayLimiterTest {

    private final ReplayLimiter limiter = new ReplayLimiter(16);

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
    void aLimitBelowOneIsRejected() {
        assertThatThrownBy(() -> new ReplayLimiter(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("standalone.persist.max-concurrent-replays");
    }

}
