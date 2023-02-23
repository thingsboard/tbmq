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
package org.thingsboard.mqtt.broker.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class BurstSubmitStrategy implements SubmitStrategy {

    private final String consumerId;
    private final boolean processInParallel;

    private List<PublishMsgWithId> orderedMsgList;

    @Override
    public void init(List<PublishMsgWithId> messages) {
        this.orderedMsgList = new ArrayList<>(messages);
    }

    @Override
    public ConcurrentMap<UUID, PublishMsgWithId> getPendingMap() {
        return orderedMsgList.stream().collect(Collectors.toConcurrentMap(PublishMsgWithId::getId, Function.identity()));
    }

    @Override
    public void process(Consumer<PublishMsgWithId> msgConsumer) {
        if (log.isDebugEnabled()) {
            log.debug("Consumer [{}] processing [{}] messages.", consumerId, orderedMsgList.size());
        }
        if (processInParallel) {
            Map<String, List<PublishMsgWithId>> msgsByClientMap = collectToMapByClientId();
            msgsByClientMap
                    .values()
                    .parallelStream()
                    .forEach(clientMsgList -> processMessages(msgConsumer, clientMsgList));
        } else {
            processMessages(msgConsumer, orderedMsgList);
        }
    }

    @Override
    public void update(Map<UUID, PublishMsgWithId> reprocessMap) {
        List<PublishMsgWithId> newOrderedMsgList = new ArrayList<>(reprocessMap.size());
        for (PublishMsgWithId msg : orderedMsgList) {
            if (reprocessMap.containsKey(msg.getId())) {
                newOrderedMsgList.add(msg);
            }
        }
        orderedMsgList = newOrderedMsgList;
    }

    private Map<String, List<PublishMsgWithId>> collectToMapByClientId() {
        Map<String, List<PublishMsgWithId>> map = new HashMap<>();
        for (var msg : orderedMsgList) {
            var clientId = getClientId(msg);
            map.computeIfAbsent(clientId, id -> new ArrayList<>()).add(msg);
        }
        return map;
    }

    private String getClientId(PublishMsgWithId msg) {
        return msg.getPublishMsgProto().getSessionInfo().getClientInfo().getClientId();
    }

    private void processMessages(Consumer<PublishMsgWithId> msgConsumer, List<PublishMsgWithId> msgList) {
        for (var msg : msgList) {
            msgConsumer.accept(msg);
        }
    }
}
