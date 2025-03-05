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
package org.thingsboard.mqtt.broker.common.data.event;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.UUID;

@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
public class LifecycleEvent extends Event {

    @Serial
    private static final long serialVersionUID = -3247420461850911549L;

    @Builder
    private LifecycleEvent(UUID entityId, String serviceId,
                           UUID id, long ts,
                           String lcEventType, boolean success, String error) {
        super(entityId, serviceId, id, ts);
        this.lcEventType = lcEventType;
        this.success = success;
        this.error = error;
    }

    private final String lcEventType;
    private final boolean success;
    @Setter
    private String error;

    @Override
    public EventType getType() {
        return EventType.LC_EVENT;
    }

    @Override
    public EventInfo toInfo() {
        EventInfo eventInfo = super.toInfo();
        var json = (ObjectNode) eventInfo.getBody();
        json.put("event", lcEventType).put("success", success);
        if (error != null) {
            json.put("error", error);
        }
        return eventInfo;
    }

}
