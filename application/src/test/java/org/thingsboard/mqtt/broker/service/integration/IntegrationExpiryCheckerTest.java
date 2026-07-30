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
package org.thingsboard.mqtt.broker.service.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationExpiryCheckerTest {

    // The checker holds the ttl in the configured unit, seconds, and converts on use; the disconnected timestamps
    // below are epoch millis, so both forms are needed.
    static final long TTL_SEC = TimeUnit.DAYS.toSeconds(7);
    static final long TTL_MS = TimeUnit.DAYS.toMillis(7);

    IntegrationExpiryChecker checker;

    @BeforeEach
    void setUp() {
        checker = new IntegrationExpiryChecker();
        ReflectionTestUtils.setField(checker, "ttlSec", TTL_SEC);
    }

    @Test
    void givenPositiveTtl_whenIsCleanupEnabled_thenTrue() {
        assertThat(checker.isCleanupEnabled()).isTrue();
    }

    @Test
    void givenZeroTtl_whenIsCleanupEnabled_thenFalse() {
        ReflectionTestUtils.setField(checker, "ttlSec", 0L);

        assertThat(checker.isCleanupEnabled()).isFalse();
    }

    @Test
    void givenNegativeTtl_whenIsCleanupEnabled_thenFalse() {
        ReflectionTestUtils.setField(checker, "ttlSec", -1L);

        assertThat(checker.isCleanupEnabled()).isFalse();
    }

    @Test
    void givenEnabledIntegration_whenIsExpired_thenFalse() {
        assertThat(checker.isExpired(newIntegration(true, System.currentTimeMillis() - TTL_MS - 1))).isFalse();
    }

    @Test
    void givenDisabledIntegrationWithinTtl_whenIsExpired_thenFalse() {
        long disconnectedTime = System.currentTimeMillis() - TTL_MS + TimeUnit.HOURS.toMillis(1);

        assertThat(checker.isExpired(newIntegration(false, disconnectedTime))).isFalse();
    }

    @Test
    void givenDisabledIntegrationPastTtl_whenIsExpired_thenTrue() {
        assertThat(checker.isExpired(newIntegration(false, System.currentTimeMillis() - TTL_MS - 1))).isTrue();
    }

    /**
     * A zero ttl disables the cleanup entirely, so it must not read as "expired always".
     */
    @Test
    void givenCleanupDisabled_whenIsExpired_thenFalseEvenForLongDisabledIntegration() {
        ReflectionTestUtils.setField(checker, "ttlSec", 0L);

        assertThat(checker.isExpired(newIntegration(false, System.currentTimeMillis() - TTL_MS - 1))).isFalse();
    }

    private Integration newIntegration(boolean enabled, long disconnectedTime) {
        Integration integration = new Integration(UUID.randomUUID());
        integration.setName("test-integration");
        integration.setEnabled(enabled);
        integration.setDisconnectedTime(disconnectedTime);
        return integration;
    }

}
