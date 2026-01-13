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

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.util.UUID;

@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
public class StatisticsEvent extends Event {

    @Serial
    private static final long serialVersionUID = 6683733979448910631L;

    @Builder
    private StatisticsEvent(UUID entityId, String serviceId, UUID id, long ts, long messagesProcessed, long errorsOccurred) {
        super(entityId, serviceId, id, ts);
        this.messagesProcessed = messagesProcessed;
        this.errorsOccurred = errorsOccurred;
    }

    private final long messagesProcessed;
    private final long errorsOccurred;

    @Override
    public EventType getType() {
        return EventType.STATS;
    }

    @Override
    public EventInfo toInfo() {
        EventInfo eventInfo = super.toInfo();
        var json = (ObjectNode) eventInfo.getBody();
        json.put("messagesProcessed", messagesProcessed).put("errorsOccurred", errorsOccurred);
        return eventInfo;
    }
}
