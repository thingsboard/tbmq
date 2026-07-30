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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;

import java.util.concurrent.TimeUnit;

/**
 * Single source of the "has this integration been disabled long enough to reclaim?" predicate, shared by the periodic
 * cleanup sweep and by the startup load that must not re-attach an integration the sweep has already detached.
 * <p>
 * Its own component rather than a method on the sweep: the startup path needs the predicate, not the sweep, and
 * depending on the sweep would couple broker bootstrap to a scheduled background job.
 */
@Component
public class IntegrationExpiryChecker {

    /**
     * Kept in the configured unit and converted where it is used, rather than resolved as
     * {@code #{${integrations.cleanup.ttl:604800} * 1000}}. Placeholder substitution runs before SpEL evaluation, so
     * that expression multiplied two int literals, and SpEL only widens to long when an operand already is one - a ttl
     * past roughly 24.8 days wrapped negative and disabled the cleanup outright, visible only as a debug line.
     */
    @Value("${integrations.cleanup.ttl:604800}")
    private long ttlSec;

    public boolean isCleanupEnabled() {
        return ttlSec > 0;
    }

    /**
     * Whether the integration is disabled and has been for longer than {@code integrations.cleanup.ttl}. Nothing is
     * removed when this is true - the row survives, only the topics and the two stream attachments go.
     * <p>
     * No integration expires while the cleanup is disabled, otherwise a zero ttl would read as "expired always".
     */
    public boolean isExpired(Integration integration) {
        // Compared as elapsed time rather than as disconnectedTime + ttl: TimeUnit saturates a huge ttl to
        // Long.MAX_VALUE instead of wrapping, and that sum would then overflow into the past and expire every
        // integration at once. Subtracting two epoch millis cannot overflow, so the saturated value reads as
        // "never expires", which is the safe direction for a misconfiguration.
        return isCleanupEnabled()
                && !integration.isEnabled()
                && System.currentTimeMillis() - integration.getDisconnectedTime() > TimeUnit.SECONDS.toMillis(ttlSec);
    }
}
