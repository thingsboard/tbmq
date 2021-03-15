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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public class ApplicationPersistedMsgCtx {
    private final Map<Long, Integer> messagesToResend;

    public Integer getMsgPacketId(Long msgOffset) {
        Integer msgPacketId = null;
        if (!messagesToResend.isEmpty()) {
            msgPacketId = messagesToResend.get(msgOffset);
            if (msgPacketId == null) {
                messagesToResend.clear();
            }
        }
        return msgPacketId;
    }

    public int getLastPacketId() {
        long lastOffset = messagesToResend.keySet().stream().max(Long::compareTo).orElse(-1L);
        return lastOffset != -1 ? messagesToResend.get(lastOffset) : 1;
    }
}
