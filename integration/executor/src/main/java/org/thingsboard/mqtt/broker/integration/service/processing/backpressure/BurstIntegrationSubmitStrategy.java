/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.integration.service.processing.backpressure;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class BurstIntegrationSubmitStrategy implements IntegrationSubmitStrategy {

    @Getter
    private final String integrationId;

    private Map<UUID, PublishIntegrationMsgProto> publishMsgMap;

    @Override
    public void init(Map<UUID, PublishIntegrationMsgProto> messages) {
        log.debug("[{}] Init pack {}", integrationId, messages.size());
        this.publishMsgMap = messages;
    }

    @Override
    public ConcurrentMap<UUID, PublishIntegrationMsgProto> getPendingMap() {
        return new ConcurrentHashMap<>(publishMsgMap);
    }

    @Override
    public void process(Consumer<Map.Entry<UUID, PublishIntegrationMsgProto>> msgConsumer) {
        log.debug("[{}] Start sending the pack of messages {}", integrationId, publishMsgMap.size());
        for (Map.Entry<UUID, PublishIntegrationMsgProto> entry : publishMsgMap.entrySet()) {
            msgConsumer.accept(entry);
        }
    }

    @Override
    public void update(Map<UUID, PublishIntegrationMsgProto> reprocessMap) {
        log.debug("[{}] Updating the pack of messages {}", integrationId, reprocessMap.size());
        publishMsgMap = reprocessMap;
    }
}
