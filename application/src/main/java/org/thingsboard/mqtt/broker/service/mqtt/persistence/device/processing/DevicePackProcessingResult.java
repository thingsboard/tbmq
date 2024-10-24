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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class DevicePackProcessingResult {

    private final Map<String, ClientIdMessagesPack> pendingMap;
    private final Map<String, ClientIdMessagesPack> failedMap;
    private final Map<String, DevicePublishMsgListAndPrevPacketId> successMap;

    public DevicePackProcessingResult(DevicePackProcessingContext ctx) {
        this.pendingMap = new HashMap<>(ctx.getPendingMap());
        this.failedMap = new HashMap<>(ctx.getFailedMap());
        this.successMap = new HashMap<>(ctx.getSuccessMap());
    }
}
