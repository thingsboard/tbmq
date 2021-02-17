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
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class BurstSubmitStrategy implements ApplicationSubmitStrategy {
    private final String clientId;
    private final Consumer<Long> successfulOffsetConsumer;

    private List<PublishMsgWithOffset> orderedMsgList;
    private AtomicLong lastCommittedOffset;

    @Override
    public void init(List<PublishMsgWithOffset> messagesWithOffset) {
        this.orderedMsgList = new ArrayList<>(messagesWithOffset);
        this.lastCommittedOffset = new AtomicLong(this.orderedMsgList.get(0).getOffset() - 1);
    }

    @Override
    public ConcurrentMap<Integer, PublishMsgWithOffset> getPendingMap() {
        return orderedMsgList.stream()
                .collect(Collectors.toConcurrentMap(msg -> msg.getPublishMsgProto().getPacketId(), Function.identity()));
    }

    @Override
    public void process(Consumer<PublishMsgWithOffset> msgConsumer) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] processing [{}] persisted messages.", clientId, orderedMsgList.size());
        }
        orderedMsgList.forEach(msgConsumer::accept);
    }

    @Override
    public void update(Map<Integer, PublishMsgWithOffset> reprocessMap) {
        List<PublishMsgWithOffset> newOrderedMsgList = new ArrayList<>(reprocessMap.size());
        for (PublishMsgWithOffset msg : orderedMsgList) {
            if (reprocessMap.containsKey(msg.getPublishMsgProto().getPacketId())) {
                newOrderedMsgList.add(msg);
            }
        }
        orderedMsgList = newOrderedMsgList;
    }

    @Override
    public void onSuccess(Long offset) {
        long prevOffset = lastCommittedOffset.get();
        if (offset > prevOffset) {
            lastCommittedOffset.getAndSet(offset);
            successfulOffsetConsumer.accept(offset);
        }
    }
}
