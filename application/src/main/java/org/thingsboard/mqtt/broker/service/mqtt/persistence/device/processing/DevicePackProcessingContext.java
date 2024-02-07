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
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DevicePackProcessingContext {
    @Getter
    private final List<DevicePublishMsg> messages;
    private final AtomicBoolean successful = new AtomicBoolean(false);
    private volatile boolean detectMsgDuplication;

    public DevicePackProcessingContext(List<DevicePublishMsg> messages) {
        this.messages = messages;
        this.detectMsgDuplication = true;
    }

    public void disableMsgDuplicationDetection(){
        this.detectMsgDuplication = false;
    }


    public boolean detectMsgDuplication(){
        return detectMsgDuplication;
    }

    public void onSuccess() {
        successful.getAndSet(true);
    }

    public boolean isSuccessful() {
        return successful.get();
    }
}
