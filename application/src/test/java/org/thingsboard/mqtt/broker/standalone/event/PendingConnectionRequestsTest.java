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
package org.thingsboard.mqtt.broker.standalone.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingConnectionRequestsTest {

    private PendingConnectionRequests pending;

    @AfterEach
    void tearDown() {
        pending.destroy();
    }

    private PendingConnectionRequests create(long maxPending, long maxRequestTimeoutMs, long cleanupIntervalMs) {
        pending = new PendingConnectionRequests(maxPending, maxRequestTimeoutMs, cleanupIntervalMs);
        pending.init();
        return pending;
    }

    @Test
    void completeResolvesTheRegisteredFuture() throws Exception {
        create(10, 60_000, 20);
        UUID id = UUID.randomUUID();
        var future = pending.register(id);

        pending.complete(id, ConnectionResponse.builder().success(true).sessionPresent(true).build());

        assertThat(future.get(1, TimeUnit.SECONDS).isSessionPresent()).isTrue();
    }

    @Test
    void failResolvesTheRegisteredFutureExceptionally() {
        create(10, 60_000, 20);
        UUID id = UUID.randomUUID();
        var future = pending.register(id);

        pending.fail(id, new IllegalStateException("boom"));

        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void anUnknownOrAlreadyAnsweredRequestIsIgnored() {
        create(10, 60_000, 20);
        UUID id = UUID.randomUUID();
        var future = pending.register(id);
        pending.complete(id, ConnectionResponse.builder().success(true).build());

        pending.complete(id, ConnectionResponse.builder().success(false).build());
        pending.fail(id, new RuntimeException());
        pending.complete(UUID.randomUUID(), ConnectionResponse.builder().success(false).build());

        assertThat(future).isDone();
        assertThat(future.resultNow().isSuccess()).isTrue();
    }

    @Test
    void aRequestNobodyAnswersTimesOut() {
        create(10, 100, 20);
        var future = pending.register(UUID.randomUUID());

        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(pending.size()).isZero();
    }

    @Test
    void aFullPendingMapFailsAtOnce() {
        create(1, 60_000, 20);
        var first = pending.register(UUID.randomUUID());

        var second = pending.register(UUID.randomUUID());

        assertThat(first).isNotDone();
        assertThat(second).isDone();
        assertThatThrownBy(second::get)
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("Pending request map is full");
    }

    @Test
    void aSlotIsFreedOnceARequestIsAnswered() {
        create(1, 60_000, 20);
        UUID id = UUID.randomUUID();
        pending.register(id);
        pending.complete(id, ConnectionResponse.builder().success(true).build());

        assertThat(pending.register(UUID.randomUUID())).isNotDone();
    }

    static SessionInfo sessionInfo(String clientId) {
        return SessionInfo.builder()
                .serviceId("lite")
                .sessionId(UUID.randomUUID())
                .cleanStart(true)
                .sessionExpiryInterval(0)
                .clientInfo(new ClientInfo(clientId, ClientType.DEVICE))
                .connectionInfo(new ConnectionInfo(1L, 0L, 60))
                .build();
    }
}
