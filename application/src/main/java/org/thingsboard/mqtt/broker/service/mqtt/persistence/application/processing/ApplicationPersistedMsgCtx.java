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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@RequiredArgsConstructor
public class ApplicationPersistedMsgCtx {

    private final Map<Long, Integer> publishMsgIds;
    private final Map<Long, Integer> pubRelMsgIds;

    public ApplicationPersistedMsgCtx() {
        this.publishMsgIds = Collections.emptyMap();
        this.pubRelMsgIds = Collections.emptyMap();
    }

    public Integer getMsgPacketId(Long msgOffset) {
        Integer msgPacketId = null;
        if (!publishMsgIds.isEmpty()) {
            msgPacketId = publishMsgIds.get(msgOffset);
            if (msgPacketId == null) {
                publishMsgIds.clear();
            }
        }
        return msgPacketId;
    }

    public int getLastPacketId() {
        long lastPublishOffset = publishMsgIds.keySet().stream().max(Long::compareTo).orElse(-1L);
        if (lastPublishOffset != -1) {
            return publishMsgIds.get(lastPublishOffset);
        }
        long lastPubRelOffset = pubRelMsgIds.keySet().stream().max(Long::compareTo).orElse(-1L);
        if (lastPubRelOffset != -1) {
            return pubRelMsgIds.get(lastPubRelOffset);
        }
        return 0;
    }
}
