/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.queue.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.integration.ComponentLifecycleEventProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationProto;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UserPropertyProto;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationProtoConverterTest {

    @Test
    void testToProto_WithDeletedEvent() {
        UUID integrationId = UUID.randomUUID();
        Integration integration = new Integration();
        integration.setId(integrationId);
        integration.setType(IntegrationType.MQTT);

        IntegrationMsgProto proto = IntegrationProtoConverter.toProto(integration, ComponentLifecycleEvent.DELETED);

        assertEquals(integrationId.getMostSignificantBits(), proto.getIntegrationIdMSB());
        assertEquals(integrationId.getLeastSignificantBits(), proto.getIntegrationIdLSB());
        assertEquals("MQTT", proto.getType());
        assertEquals(ComponentLifecycleEventProto.forNumber(ComponentLifecycleEvent.DELETED.ordinal()), proto.getEvent());
    }

    @Test
    void testToProto_WithCreatedEvent() {
        UUID integrationId = UUID.randomUUID();
        Integration integration = new Integration();
        integration.setId(integrationId);
        integration.setType(IntegrationType.HTTP);
        integration.setName("TestIntegration");
        integration.setEnabled(true);
        integration.setConfiguration(JacksonUtil.newObjectNode().put("key", "value"));

        IntegrationMsgProto proto = IntegrationProtoConverter.toProto(integration, ComponentLifecycleEvent.CREATED);

        assertEquals(integrationId.getMostSignificantBits(), proto.getIntegrationIdMSB());
        assertEquals(integrationId.getLeastSignificantBits(), proto.getIntegrationIdLSB());
        assertEquals("HTTP", proto.getType());
        assertEquals("TestIntegration", proto.getName());
        assertTrue(proto.getEnabled());
        assertEquals(ComponentLifecycleEventProto.forNumber(ComponentLifecycleEvent.CREATED.ordinal()), proto.getEvent());
        assertEquals("{\"key\":\"value\"}", proto.getConfiguration());
    }

    @Test
    void testFromProto() {
        UUID integrationId = UUID.randomUUID();
        IntegrationMsgProto proto = IntegrationMsgProto.newBuilder()
                .setIntegrationIdMSB(integrationId.getMostSignificantBits())
                .setIntegrationIdLSB(integrationId.getLeastSignificantBits())
                .setType("KAFKA")
                .setEvent(ComponentLifecycleEventProto.forNumber(ComponentLifecycleEvent.REINIT.ordinal()))
                .setName("KafkaIntegration")
                .setEnabled(true)
                .setConfiguration("{\"kafka\":\"config\"}")
                .build();

        IntegrationLifecycleMsg msg = IntegrationProtoConverter.fromProto(proto);

        assertEquals(integrationId, msg.getIntegrationId());
        assertEquals(IntegrationType.KAFKA, msg.getType());
        assertEquals(ComponentLifecycleEvent.REINIT, msg.getEvent());
        assertEquals("KafkaIntegration", msg.getName());
        assertTrue(msg.isEnabled());
        assertEquals("{\"kafka\":\"config\"}", msg.getConfiguration().toString());
    }

    @Test
    void testToProto_Integration() {
        UUID integrationId = UUID.randomUUID();
        Integration integration = new Integration();
        integration.setId(integrationId);
        integration.setCreatedTime(123456789L);
        integration.setType(IntegrationType.MQTT);
        integration.setName("MQTTIntegration");
        integration.setEnabled(true);
        integration.setConfiguration(JacksonUtil.newObjectNode().put("configKey", "configValue"));
        integration.setAdditionalInfo(JacksonUtil.newObjectNode().put("infoKey", "infoValue"));

        IntegrationProto proto = IntegrationProtoConverter.toProto(integration);

        assertEquals(integrationId.getMostSignificantBits(), proto.getIntegrationIdMSB());
        assertEquals(integrationId.getLeastSignificantBits(), proto.getIntegrationIdLSB());
        assertEquals(123456789L, proto.getCreatedTime());
        assertEquals("MQTT", proto.getType());
        assertEquals("MQTTIntegration", proto.getName());
        assertTrue(proto.getEnabled());
        assertEquals("{\"configKey\":\"configValue\"}", proto.getConfiguration());
        assertEquals("{\"infoKey\":\"infoValue\"}", proto.getAdditionalInfo());
    }

    @Test
    void testFromProto_Integration() {
        UUID integrationId = UUID.randomUUID();
        IntegrationProto proto = IntegrationProto.newBuilder()
                .setIntegrationIdMSB(integrationId.getMostSignificantBits())
                .setIntegrationIdLSB(integrationId.getLeastSignificantBits())
                .setCreatedTime(987654321L)
                .setType("HTTP")
                .setName("HttpIntegration")
                .setEnabled(false)
                .setConfiguration("{\"http\":\"config\"}")
                .setAdditionalInfo("{\"extra\":\"info\"}")
                .build();

        Integration integration = IntegrationProtoConverter.fromProto(proto);

        assertEquals(integrationId, integration.getId());
        assertEquals(987654321L, integration.getCreatedTime());
        assertEquals(IntegrationType.HTTP, integration.getType());
        assertEquals("HttpIntegration", integration.getName());
        assertFalse(integration.isEnabled());
        assertEquals("{\"http\":\"config\"}", integration.getConfiguration().toString());
        assertEquals("{\"extra\":\"info\"}", integration.getAdditionalInfo().toString());
    }

    @Test
    void testToProto_PublishIntegrationMsgProto() {
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder()
                .setTopicName("test/topic")
                .setPayload(ByteString.copyFromUtf8("payload-data"))
                .build();

        PublishIntegrationMsgProto proto = IntegrationProtoConverter.toProto(publishMsgProto, "test-service");

        assertEquals(publishMsgProto, proto.getPublishMsgProto());
        assertEquals("test-service", proto.getTbmqNode());
        assertTrue(proto.getTimestamp() > 0);
    }

    @Test
    void testFromProto_UserProperties() {
        List<UserPropertyProto> userProperties = List.of(
                UserPropertyProto.newBuilder().setKey("prop1").setValue("value1").build(),
                UserPropertyProto.newBuilder().setKey("prop2").setValue("value2").build()
        );

        JsonNode jsonNode = IntegrationProtoConverter.fromProto(userProperties);

        assertTrue(jsonNode.has("prop1"));
        assertEquals("value1", jsonNode.get("prop1").asText());
        assertTrue(jsonNode.has("prop2"));
        assertEquals("value2", jsonNode.get("prop2").asText());
    }
}
