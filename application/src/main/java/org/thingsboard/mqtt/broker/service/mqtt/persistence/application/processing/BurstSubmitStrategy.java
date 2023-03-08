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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class BurstSubmitStrategy implements ApplicationSubmitStrategy {

    @Getter
    private final String clientId;

    private List<PersistedMsg> orderedMessages;

    @Override
    public void init(List<PersistedMsg> orderedMessages) {
        this.orderedMessages = new ArrayList<>(orderedMessages);
    }

    @Override
    public ConcurrentMap<Integer, PersistedMsg> getPendingMap() {
        return orderedMessages.stream().collect(Collectors.toConcurrentMap(PersistedMsg::getPacketId, Function.identity()));
    }

    @Override
    public void process(Consumer<PersistedMsg> msgConsumer) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] processing [{}] persisted messages.", clientId, orderedMessages.size());
        }
        orderedMessages.forEach(msgConsumer::accept);
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
