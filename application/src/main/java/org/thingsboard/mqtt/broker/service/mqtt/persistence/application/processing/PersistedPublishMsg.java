/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
public class PersistedPublishMsg implements PersistedMsg {
    @Getter
    private final PublishMsg publishMsg;
    private final long offset;

    @Override
    public long getPacketOffset() {
        return offset;
    }

    @Override
    public int getPacketId() {
        return publishMsg.getPacketId();
    }

    @Override
    public PersistedPacketType getPacketType() {
        return PersistedPacketType.PUBLISH;
    }
}
