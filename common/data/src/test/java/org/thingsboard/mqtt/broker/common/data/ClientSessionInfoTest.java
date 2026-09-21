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
package org.thingsboard.mqtt.broker.common.data;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientSessionInfoTest {

    private static final long NOW = 1_000_000_000L;

    @Test
    public void givenDisconnectedSessionPastExpiryInterval_whenIsExpired_thenTrue() {
        assertTrue(session(false, true, 3, NOW - TimeUnit.SECONDS.toMillis(16)).isExpired(NOW));
    }

    @Test
    public void givenDisconnectedSessionInsideExpiryInterval_whenIsExpired_thenFalse() {
        assertFalse(session(false, true, 30, NOW - TimeUnit.SECONDS.toMillis(1)).isExpired(NOW));
    }

    @Test
    public void givenConnectedSession_whenIsExpired_thenFalse() {
        assertFalse(session(true, true, 3, NOW - TimeUnit.DAYS.toMillis(1)).isExpired(NOW));
    }

    @Test
    public void givenMqtt3NotCleanSession_whenIsExpired_thenFalse() {
        assertFalse(session(false, false, 0, NOW - TimeUnit.DAYS.toMillis(30)).isExpired(NOW));
    }

    @Test
    public void givenDisconnectedCleanSessionWithZeroExpiry_whenIsExpired_thenTrue() {
        assertTrue(session(false, true, 0, NOW - 1).isExpired(NOW));
    }

    private static ClientSessionInfo session(boolean connected, boolean cleanStart, int expiry, long disconnectedAt) {
        return ClientSessionInfo.builder()
                .clientId("c")
                .connected(connected)
                .cleanStart(cleanStart)
                .sessionExpiryInterval(expiry)
                .disconnectedAt(disconnectedAt)
                .build();
    }
}
