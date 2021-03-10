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
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.thingsboard.mqtt.broker.common.data.DeviceSessionCtx;
import org.thingsboard.mqtt.broker.common.data.PublishedMsgInfo;
import org.thingsboard.mqtt.broker.dao.util.mapping.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonStringType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode
@Entity
@TypeDef(name = "json", typeClass = JsonStringType.class)
@Table(name = ModelConstants.DEVICE_SESSION_CTX_COLUMN_FAMILY_NAME)
public class DeviceSessionCtxEntity implements ToData<DeviceSessionCtx> {
    @Id
    @Column(name = ModelConstants.MQTT_CLIENT_CLIENT_ID_PROPERTY)
    private String clientId;

    @Type(type = "json")
    @Column(name = ModelConstants.DEVICE_SESSION_CTX_DATA_PROPERTY)
    private JsonNode data;

    public DeviceSessionCtxEntity() {}

    public DeviceSessionCtxEntity(DeviceSessionCtx deviceSessionCtx) {
        this.clientId = deviceSessionCtx.getClientId();
        this.data = JacksonUtil.toJsonNode(JacksonUtil.toString(deviceSessionCtx.getPublishedMsgInfos()));
    }

    @Override
    public DeviceSessionCtx toData() {
        ArrayNode publishedMsgInfoJsonArray = (ArrayNode) this.data;
        List<PublishedMsgInfo> publishedMsgInfos = new ArrayList<>(publishedMsgInfoJsonArray.size());
        for (JsonNode publishedMsgInfoJson : publishedMsgInfoJsonArray) {
            publishedMsgInfos.add(JacksonUtil.toValue(publishedMsgInfoJson, PublishedMsgInfo.class));
        }
        return DeviceSessionCtx.builder()
                .clientId(clientId)
                .publishedMsgInfos(publishedMsgInfos)
                .build();
    }
}
