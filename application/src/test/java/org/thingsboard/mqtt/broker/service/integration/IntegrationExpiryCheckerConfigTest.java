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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards how {@code integrations.cleanup.ttl} is turned into milliseconds. A ttl beyond the int range - anything past
 * roughly 24.8 days - used to overflow while being resolved and silently disable the cleanup altogether: the seconds
 * value was substituted into the SpEL expression before evaluation, leaving two int literals, and SpEL only widens to
 * long when an operand already is one. Thirty days evaluated to -1702967296, so isCleanupEnabled answered false and
 * nothing was ever reclaimed, with only a debug line to show for it.
 * <p>
 * {@link IntegrationExpiryCheckerTest} sets the field directly and so cannot see any of this - only a resolved context
 * can.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = IntegrationExpiryChecker.class)
@TestPropertySource(properties = "integrations.cleanup.ttl=2592000")
public class IntegrationExpiryCheckerConfigTest {

    private static final long THIRTY_DAYS_MS = TimeUnit.DAYS.toMillis(30);

    @Autowired
    IntegrationExpiryChecker checker;

    @Test
    public void givenTtlBeyondTheIntRange_whenIsCleanupEnabled_thenTrue() {
        assertThat(checker.isCleanupEnabled()).isTrue();
    }

    /**
     * Not just "enabled": asserting on both sides of the boundary is what proves the configured ttl survived the
     * conversion intact, rather than merely staying positive.
     */
    @Test
    public void givenTtlBeyondTheIntRange_whenIsExpired_thenHonoursTheWholeTtl() {
        long justPast = System.currentTimeMillis() - THIRTY_DAYS_MS - TimeUnit.SECONDS.toMillis(1);
        long wellWithin = System.currentTimeMillis() - THIRTY_DAYS_MS + TimeUnit.HOURS.toMillis(1);

        assertThat(checker.isExpired(disabledSince(justPast))).isTrue();
        assertThat(checker.isExpired(disabledSince(wellWithin))).isFalse();
    }

    private Integration disabledSince(long disconnectedTime) {
        Integration integration = new Integration(UUID.randomUUID());
        integration.setName("test-integration");
        integration.setEnabled(false);
        integration.setDisconnectedTime(disconnectedTime);
        return integration;
    }

}
