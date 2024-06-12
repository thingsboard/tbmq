/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.ApplicationMsgInfo;
import org.thingsboard.mqtt.broker.common.data.ApplicationSessionCtx;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonConverter;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode
@Entity
@Table(name = ModelConstants.APPLICATION_SESSION_CTX_COLUMN_FAMILY_NAME)
public class ApplicationSessionCtxEntity implements ToData<ApplicationSessionCtx> {

    @Id
    @Column(name = ModelConstants.APPLICATION_SESSION_CTX_CLIENT_ID_PROPERTY)
    private String clientId;

    @Column(name = ModelConstants.APPLICATION_SESSION_CTX_LAST_UPDATED_PROPERTY)
    private long lastUpdatedTime;

    @Convert(converter = JsonConverter.class)
    @Column(name = ModelConstants.APPLICATION_SESSION_CTX_PUBLISH_MSG_INFOS_PROPERTY)
    private JsonNode publishMsgInfos;
    @Convert(converter = JsonConverter.class)
    @Column(name = ModelConstants.APPLICATION_SESSION_CTX_PUBREL_MSG_INFOS_PROPERTY)
    private JsonNode pubRelMsgInfos;

    public ApplicationSessionCtxEntity() {
    }

    public ApplicationSessionCtxEntity(ApplicationSessionCtx applicationSessionCtx) {
        this.clientId = applicationSessionCtx.getClientId();
        this.lastUpdatedTime = applicationSessionCtx.getLastUpdatedTime();
        this.publishMsgInfos = JacksonUtil.toJsonNode(JacksonUtil.toString(applicationSessionCtx.getPublishMsgInfos()));
        this.pubRelMsgInfos = JacksonUtil.toJsonNode(JacksonUtil.toString(applicationSessionCtx.getPubRelMsgInfos()));
    }

    @Override
    public ApplicationSessionCtx toData() {
        ArrayNode publishMsgInfoJsonArray = (ArrayNode) this.publishMsgInfos;
        List<ApplicationMsgInfo> publishMsgInfos = new ArrayList<>(publishMsgInfoJsonArray.size());
        for (JsonNode publishMsgInfoJson : publishMsgInfoJsonArray) {
            publishMsgInfos.add(JacksonUtil.toValue(publishMsgInfoJson, ApplicationMsgInfo.class));
        }
        ArrayNode pubRelMsgInfoJsonArray = (ArrayNode) this.pubRelMsgInfos;
        List<ApplicationMsgInfo> pubRelMsgInfos = new ArrayList<>(publishMsgInfoJsonArray.size());
        for (JsonNode pubRelMsgInfoJson : pubRelMsgInfoJsonArray) {
            pubRelMsgInfos.add(JacksonUtil.toValue(pubRelMsgInfoJson, ApplicationMsgInfo.class));
        }
        return ApplicationSessionCtx.builder()
                .clientId(clientId)
                .lastUpdatedTime(lastUpdatedTime)
                .publishMsgInfos(publishMsgInfos)
                .pubRelMsgInfos(pubRelMsgInfos)
                .build();
    }
}
