/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonStringType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.IOException;
import java.util.Set;

@Data
@EqualsAndHashCode
@Entity
@TypeDef(name = "json", typeClass = JsonStringType.class)
@Table(name = ModelConstants.GENERIC_CLIENT_SESSION_CTX_COLUMN_FAMILY_NAME)
public class GenericClientSessionCtxEntity implements ToData<GenericClientSessionCtx> {
    @Id
    @Column(name = ModelConstants.GENERIC_CLIENT_SESSION_CTX_CLIENT_ID_PROPERTY)
    private String clientId;

    @Column(name = ModelConstants.GENERIC_CLIENT_SESSION_CTX_LAST_UPDATED_PROPERTY)
    private long lastUpdatedTime;

    @Type(type = "json")
    @Column(name = ModelConstants.GENERIC_CLIENT_SESSION_CTX_QOS2_PUBLISH_PACKET_IDS_PROPERTY)
    private JsonNode qos2PublishPacketIds;

    public GenericClientSessionCtxEntity() {
    }

    public GenericClientSessionCtxEntity(GenericClientSessionCtx genericClientSessionCtx) {
        this.clientId = genericClientSessionCtx.getClientId();
        this.lastUpdatedTime = genericClientSessionCtx.getLastUpdatedTime();
        this.qos2PublishPacketIds = JacksonUtil.toJsonNode(JacksonUtil.toString(genericClientSessionCtx.getQos2PublishPacketIds()));
    }

    @Override
    public GenericClientSessionCtx toData() {
        Set<Integer> qos2PublishPacketIds;
        try {
            qos2PublishPacketIds = Set.of(JacksonUtil.OBJECT_MAPPER.treeToValue(this.qos2PublishPacketIds, Integer[].class));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return GenericClientSessionCtx.builder()
                .clientId(clientId)
                .lastUpdatedTime(lastUpdatedTime)
                .qos2PublishPacketIds(qos2PublishPacketIds)
                .build();
    }
}
