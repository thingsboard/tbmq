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
package org.thingsboard.mqtt.broker.service.mqtt;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class StandaloneClientManager implements ClientManager {
    private final Set<String> connectedClients = Sets.newConcurrentHashSet();

    private final AtomicInteger connectedClientsCounter;

    public StandaloneClientManager(StatsManager statsManager) {
        this.connectedClientsCounter = statsManager.createConnectedClientsCounter();
    }

    @Override
    public boolean registerClient(String clientId) throws MqttException {
        boolean successfullyAdded = connectedClients.add(clientId);
        if (successfullyAdded) {
            connectedClientsCounter.incrementAndGet();
        }
        return successfullyAdded;
    }

    @Override
    public void unregisterClient(String clientId) {
        boolean successfullyRemoved = connectedClients.remove(clientId);
        if (successfullyRemoved) {
            connectedClientsCounter.decrementAndGet();
        }
    }
}
