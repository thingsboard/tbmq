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
package org.thingsboard.mqtt.broker.service.state;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.integration.ComponentLifecycleEvent;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.integration.api.TbPlatformIntegration;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Data
@RequiredArgsConstructor
public class IntegrationState {

    private final Lock updateLock = new ReentrantLock();
    private final Queue<ComponentLifecycleEvent> updateQueue = new ConcurrentLinkedQueue<>();
    private final UUID integrationId;

    private volatile ComponentLifecycleEvent currentState;
    private volatile TbPlatformIntegration integration;
    private volatile IntegrationLifecycleMsg lifecycleMsg;

}
