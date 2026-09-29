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
package org.thingsboard.mqtt.broker.integration.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Stop halts the consumer before the client: the other way round, the consumer keeps polling while doStopClient()
 * drains or closes, every message of those packs fails with "not initialized", and SKIP_ALL commits them away.
 */
class AbstractIntegrationStopOrderTest {

    private static final UUID ID = UUID.fromString("0198e1a0-1111-2222-3333-444455556666");

    private final List<String> calls = new CopyOnWriteArrayList<>();

    private class RecordingIntegration extends AbstractIntegration {

        @Override
        public void process(PublishIntegrationMsgProto msg, IntegrationMsgCallback callback) {
        }

        @Override
        protected void doProcessLifecycleEvent(ObjectNode body, IntegrationMsgCallback callback) {
        }

        @Override
        public void doStopClient() {
            calls.add("client");
        }
    }

    private RecordingIntegration started() {
        RecordingIntegration integration = new RecordingIntegration();
        integration.lifecycleMsg = IntegrationLifecycleMsg.builder().integrationId(ID).name("ie").build();
        integration.context = mock(IntegrationContext.class);
        doAnswer(inv -> calls.add("consumer")).when(integration.context).stopProcessingPersistedMessages(anyString());
        return integration;
    }

    @Test
    void destroyStopsTheConsumerBeforeTheClient() {
        started().destroy();

        assertThat(calls).containsExactly("consumer", "client");
    }

    @Test
    void destroyAndClearDataStopsTheConsumerBeforeTheClient() {
        started().destroyAndClearData();

        assertThat(calls).startsWith("consumer", "client");
    }

    @Test
    void uninitializedIntegrationStillStopsItsClient() {
        new RecordingIntegration().destroy();

        assertThat(calls).containsExactly("client");
    }
}
