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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing;

import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class BurstSubmitStrategy implements DeviceSubmitStrategy {

    private final String consumerId;

    private Map<String, ClientIdMessagesPack> clientIdToMessagesMap;

    @Override
    public void init(Map<String, ClientIdMessagesPack> clientIdToMessagesMap) {
        this.clientIdToMessagesMap = clientIdToMessagesMap;
    }

    @Override
    public ConcurrentMap<String, ClientIdMessagesPack> getPendingMap() {
        return new ConcurrentHashMap<>(clientIdToMessagesMap);
    }

    @Override
    public void process(Consumer<ClientIdMessagesPack> clientIdMessagesPackConsumer) {
        if (log.isDebugEnabled()) {
            log.debug("Consumer [{}] processing [{}] clientIds.", consumerId, clientIdToMessagesMap.size());
        }
        processClientIdPacks(clientIdMessagesPackConsumer, clientIdToMessagesMap.values());
    }

    @Override
    public void update(Map<String, ClientIdMessagesPack> reprocessMap) {
        Map<String, ClientIdMessagesPack> newClientIdToMessagesMap = Maps.newLinkedHashMapWithExpectedSize(reprocessMap.size());
        for (var pack : clientIdToMessagesMap.values()) {
            if (reprocessMap.containsKey(pack.clientId())) {
                newClientIdToMessagesMap.put(pack.clientId(), pack);
            }
        }
        clientIdToMessagesMap = newClientIdToMessagesMap;
    }

    private void processClientIdPacks(Consumer<ClientIdMessagesPack> clientIdMessagesPackConsumer, Collection<ClientIdMessagesPack> packList) {
        for (var msg : packList) {
            clientIdMessagesPackConsumer.accept(msg);
        }
    }
}
