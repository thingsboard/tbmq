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
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.integration.api.callback.IntegrationMsgCallback;
import org.thingsboard.mqtt.broker.integration.api.data.ContentType;
import org.thingsboard.mqtt.broker.integration.api.data.UplinkMetaData;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractIntegrationPublishBodyTest {

    static class TestIntegration extends AbstractIntegration {
        @Override
        public void process(PublishIntegrationMsgProto msg, IntegrationMsgCallback callback) {
        }

        ObjectNode body(PublishIntegrationMsgProto msg) {
            return constructBody(msg);
        }

        ObjectNode body(PublishIntegrationMsgProto msg, boolean includePayload) {
            return constructBody(msg, includePayload);
        }
    }

    private TestIntegration integration;

    @BeforeEach
    void setUp() {
        integration = new TestIntegration();
        IntegrationContext context = mock(IntegrationContext.class);
        when(context.getServiceId()).thenReturn("ie-1");
        integration.context = context;
        integration.metadataTemplate = new UplinkMetaData(ContentType.JSON, Map.of());
    }

    private static PublishIntegrationMsgProto message(PublishMsgProto.Builder publishMsg) {
        return PublishIntegrationMsgProto.newBuilder()
                .setPublishMsgProto(publishMsg.setClientId("c1").setTopicName("t").setPayload(ByteString.copyFromUtf8("hi")))
                .setTbmqNode("tbmq-1")
                .setTimestamp(1000L)
                .build();
    }

    @Test
    void givenUsername_whenConstructBody_thenBodyHasUsername() {
        ObjectNode body = integration.body(message(PublishMsgProto.newBuilder().setUsername("alice")));

        assertThat(body.get("username").asText()).isEqualTo("alice");
    }

    @Test
    void givenNoUsername_whenConstructBody_thenBodyHasNoUsernameKey() {
        ObjectNode body = integration.body(message(PublishMsgProto.newBuilder()));

        assertThat(body.has("username")).isFalse();
    }

    @Test
    void givenEmptyUsername_whenConstructBody_thenBodyHasNoUsernameKey() {
        ObjectNode body = integration.body(message(PublishMsgProto.newBuilder().setUsername("")));

        assertThat(body.has("username")).isFalse();
    }

    @Test
    void givenIncludePayloadFalse_whenConstructBody_thenNoPayloadButOtherFields() {
        ObjectNode body = integration.body(message(PublishMsgProto.newBuilder().setUsername("alice")), false);

        assertThat(body.has("payload")).isFalse();
        assertThat(body.get("clientId").asText()).isEqualTo("c1");
        assertThat(body.get("topicName").asText()).isEqualTo("t");
        assertThat(body.get("username").asText()).isEqualTo("alice");
    }

    @Test
    void givenDefaultOverload_whenConstructBody_thenPayloadIncluded() {
        ObjectNode body = integration.body(message(PublishMsgProto.newBuilder()));

        assertThat(body.has("payload")).isTrue();
    }
}
