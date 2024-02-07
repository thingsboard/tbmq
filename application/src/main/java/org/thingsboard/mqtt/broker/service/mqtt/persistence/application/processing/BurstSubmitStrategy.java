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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class BurstSubmitStrategy implements ApplicationSubmitStrategy {

    @Getter
    private final String clientId;

    private List<PersistedMsg> orderedMessages;

    @Override
    public void init(List<PersistedMsg> orderedMessages) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Init pack {}", clientId, orderedMessages);
        }
        this.orderedMessages = new ArrayList<>(orderedMessages);
    }

    // note, changed from collecting to map by packet id
    @Override
    public List<PersistedMsg> getOrderedMessages() {
        return orderedMessages;
    }

    @Override
    public void process(Consumer<PersistedMsg> msgConsumer) {
        for (PersistedMsg msg : orderedMessages) {
            msgConsumer.accept(msg);
        }
    }

    @Override
    public void update(Map<Integer, PersistedMsg> reprocessMap) {
        List<PersistedMsg> newOrderedMessages = new ArrayList<>(reprocessMap.size());
        for (PersistedMsg msg : orderedMessages) {
            if (reprocessMap.containsKey(msg.getPacketId())) {
                newOrderedMessages.add(msg);
            }
        }
        orderedMessages = newOrderedMessages;
    }
}
