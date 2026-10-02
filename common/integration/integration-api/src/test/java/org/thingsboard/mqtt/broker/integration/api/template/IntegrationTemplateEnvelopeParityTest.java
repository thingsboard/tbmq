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
package org.thingsboard.mqtt.broker.integration.api.template;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.integration.ClientLifecycleEventType;
import org.thingsboard.mqtt.broker.gen.integration.ClientLifecycleEventMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UserPropertyProto;
import org.thingsboard.mqtt.broker.integration.api.AbstractIntegration;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;
import org.thingsboard.mqtt.broker.integration.api.data.ContentType;
import org.thingsboard.mqtt.broker.integration.api.data.UplinkMetaData;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Keeps {@link IntegrationTemplate#ROOTS} in step with the JSON bodies the integrations actually build. Every root must
 * be written by some body, and every scalar field of the message body, plus every field common to all lifecycle event
 * types, must be a root. Renaming one, or adding one without updating the template names, turns this red. Per-type
 * event fields (e.g. {@code reason}, {@code topic}) are deliberately not roots. {@link #fullMessage()} and
 * {@link #fullEvent} must set every common proto field the body builders read, or a new one goes unnoticed.
 */
class IntegrationTemplateEnvelopeParityTest {

    static class Probe extends AbstractIntegration {
        Probe() {
            IntegrationContext ctx = mock(IntegrationContext.class);
            when(ctx.getServiceId()).thenReturn("ie-1");
            this.context = ctx;
            this.metadataTemplate = new UplinkMetaData(ContentType.JSON, Map.of("integrationName", "kafka"));
        }

        @Override
        public void process(PublishIntegrationMsgProto msg, IntegrationMsgCallback callback) {
        }

        ObjectNode message(PublishIntegrationMsgProto msg) {
            return constructBody(msg);
        }

        ObjectNode event(ClientLifecycleEventMsgProto msg) {
            return constructLifecycleEventBody(msg);
        }
    }

    private final Probe probe = new Probe();

    @Test
    void everyRootIsWrittenByAFullMessageOrEvent() {
        Set<String> written = new TreeSet<>(keysOf(probe.message(fullMessage())));
        for (ClientLifecycleEventType type : ClientLifecycleEventType.values()) {
            written.addAll(keysOf(probe.event(fullEvent(type))));
        }

        assertThat(written).containsAll(IntegrationTemplate.ROOTS);
    }

    @Test
    void everyScalarFieldOfAFullMessageIsARoot() {
        Set<String> scalars = keysOf(probe.message(fullMessage()));
        scalars.removeAll(Set.of("payload", "props", "metadata"));

        assertThat(IntegrationTemplate.ROOTS).containsAll(scalars);
    }

    @Test
    void everyFieldCommonToAllEventTypesIsARoot() {
        Set<String> common = null;
        for (ClientLifecycleEventType type : ClientLifecycleEventType.values()) {
            Set<String> keys = keysOf(probe.event(fullEvent(type)));
            if (common == null) {
                common = keys;
            } else {
                common.retainAll(keys);
            }
        }
        common.remove("metadata");

        assertThat(IntegrationTemplate.ROOTS).containsAll(common);
    }

    @Test
    void nestedRootsAreObjects() {
        ObjectNode message = probe.message(fullMessage());
        ObjectNode event = probe.event(fullEvent(ClientLifecycleEventType.CLIENT_CONNECTED));

        assertThat(message.get("props").isObject()).isTrue();
        assertThat(message.get("metadata").isObject()).isTrue();
        assertThat(event.get("metadata").isObject()).isTrue();
    }

    private static Set<String> keysOf(ObjectNode node) {
        Set<String> keys = new TreeSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static PublishIntegrationMsgProto fullMessage() {
        return PublishIntegrationMsgProto.newBuilder()
                .setPublishMsgProto(PublishMsgProto.newBuilder()
                        .setClientId("c1")
                        .setUsername("alice")
                        .setTopicName("sensors/1")
                        .setQos(1)
                        .setRetain(true)
                        .setPayload(ByteString.copyFromUtf8("p"))
                        .setClientCertCn("CN=dev")
                        .addUserProperties(UserPropertyProto.newBuilder().setKey("k").setValue("v")))
                .setTbmqNode("tbmq-1")
                .setTimestamp(1000L)
                .build();
    }

    private static ClientLifecycleEventMsgProto fullEvent(ClientLifecycleEventType type) {
        return ClientLifecycleEventMsgProto.newBuilder()
                .setEventType(type.name())
                .setClientId("c1")
                .setUsername("alice")
                .setSessionId("s1")
                .setIpAddress("10.0.0.1")
                .setTs(1000L)
                .setTbmqNode("tbmq-1")
                .setClientCertCn("CN=dev")
                .build();
    }
}
