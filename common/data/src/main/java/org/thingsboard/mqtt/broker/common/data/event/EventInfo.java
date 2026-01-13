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
package org.thingsboard.mqtt.broker.common.data.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.BaseData;

import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.BaseDataWithAdditionalInfo.getJson;
import static org.thingsboard.mqtt.broker.common.data.BaseDataWithAdditionalInfo.setJson;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@Schema
public class EventInfo extends BaseData {

    @Schema(description = "Event type", example = "STATS")
    private String type;
    @Schema(description = "UUID Entity Id for which event is created.", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID entityId;
    @Schema(description = "Event body.", implementation = JsonNode.class)
    private transient JsonNode body;
    @JsonIgnore
    private byte[] bodyBytes;

    public EventInfo() {
        super();
    }

    public EventInfo(UUID id) {
        super(id);
    }

    public EventInfo(EventInfo event) {
        super(event);
        this.setBody(event.getBody());
    }

    public JsonNode getBody() {
        return getJson(() -> body, () -> bodyBytes);
    }

    public void setBody(JsonNode body) {
        setJson(body, json -> this.body = json, bytes -> this.bodyBytes = bytes);
    }

    @Schema(description = "Timestamp of the event creation, in milliseconds", example = "1609459200000", accessMode = Schema.AccessMode.READ_ONLY)
    @Override
    public long getCreatedTime() {
        return super.getCreatedTime();
    }
}
