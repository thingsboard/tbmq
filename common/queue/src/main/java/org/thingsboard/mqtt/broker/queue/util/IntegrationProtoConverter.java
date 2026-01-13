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
package org.thingsboard.mqtt.broker.queue.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.gen.integration.ComponentLifecycleEventProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationProto.Builder;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UserPropertyProto;

import java.util.List;
import java.util.UUID;

@Slf4j
public class IntegrationProtoConverter {

    /**
     * Integration messages conversion
     */

    public static IntegrationMsgProto toProto(Integration integration, ComponentLifecycleEvent event) {

        if (event.equals(ComponentLifecycleEvent.DELETED)) {
            return IntegrationMsgProto.newBuilder()
                    .setIntegrationIdMSB(integration.getId().getMostSignificantBits())
                    .setIntegrationIdLSB(integration.getId().getLeastSignificantBits())
                    .setType(integration.getType().name())
                    .setEvent(ComponentLifecycleEventProto.forNumber(event.ordinal()))
                    .build();
        }

        // CREATED or UPDATED or REINIT
        return IntegrationMsgProto.newBuilder()
                .setIntegrationIdMSB(integration.getId().getMostSignificantBits())
                .setIntegrationIdLSB(integration.getId().getLeastSignificantBits())
                .setType(integration.getType().name())
                .setName(integration.getName())
                .setEnabled(integration.isEnabled())
                .setConfiguration(JacksonUtil.toString(integration.getConfiguration()))
                .setEvent(ComponentLifecycleEventProto.forNumber(event.ordinal()))
                .build();
    }

    public static IntegrationLifecycleMsg fromProto(IntegrationMsgProto proto) {
        IntegrationLifecycleMsg.IntegrationLifecycleMsgBuilder msg = IntegrationLifecycleMsg
                .builder()
                .integrationId(new UUID(proto.getIntegrationIdMSB(), proto.getIntegrationIdLSB()))
                .type(IntegrationType.valueOf(proto.getType()))
                .event(ComponentLifecycleEvent.values()[proto.getEventValue()]);

        if (proto.hasName()) {
            msg.name(proto.getName());
        }
        if (proto.hasEnabled()) {
            msg.enabled(proto.getEnabled());
        }
        if (proto.hasConfiguration()) {
            msg.configuration(JacksonUtil.toJsonNode(proto.getConfiguration()));
        }

        return msg.build();
    }

    public static IntegrationProto toProto(Integration integration) {
        Builder builder = IntegrationProto.newBuilder()
                .setIntegrationIdMSB(integration.getId().getMostSignificantBits())
                .setIntegrationIdLSB(integration.getId().getLeastSignificantBits())
                .setCreatedTime(integration.getCreatedTime())
                .setType(integration.getType().name())
                .setName(integration.getName())
                .setEnabled(integration.isEnabled())
                .setConfiguration(JacksonUtil.toString(integration.getConfiguration()));
        if (integration.getAdditionalInfo() != null) {
            builder.setAdditionalInfo(JacksonUtil.toString(integration.getAdditionalInfo()));
        }
        return builder.build();
    }

    public static Integration fromProto(IntegrationProto integrationProto) {
        Integration integration = new Integration();
        integration.setId(new UUID(integrationProto.getIntegrationIdMSB(), integrationProto.getIntegrationIdLSB()));
        integration.setCreatedTime(integrationProto.getCreatedTime());
        integration.setType(IntegrationType.valueOf(integrationProto.getType()));
        integration.setName(integrationProto.getName());
        integration.setEnabled(integrationProto.getEnabled());
        integration.setConfiguration(JacksonUtil.toJsonNode(integrationProto.getConfiguration()));
        integration.setAdditionalInfo(JacksonUtil.toJsonNode(integrationProto.getAdditionalInfo()));
        return integration;
    }

    public static PublishIntegrationMsgProto toProto(PublishMsgProto publishMsgProto, String tbmqServiceId) {
        return PublishIntegrationMsgProto
                .newBuilder()
                .setPublishMsgProto(publishMsgProto)
                .setTbmqNode(tbmqServiceId)
                .setTimestamp(System.currentTimeMillis())
                .build();
    }

    public static JsonNode fromProto(List<UserPropertyProto> userPropertiesList) {
        ObjectNode objectNode = JacksonUtil.newObjectNode();
        for (UserPropertyProto userPropertyProto : userPropertiesList) {
            objectNode.put(userPropertyProto.getKey(), userPropertyProto.getValue());
        }
        return objectNode;
    }
}
