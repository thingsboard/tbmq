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
package org.thingsboard.mqtt.broker.actors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.service.ActorProcessor;
import org.thingsboard.mqtt.broker.actors.client.service.MqttMessageHandler;
import org.thingsboard.mqtt.broker.actors.client.service.connect.ConnectService;
import org.thingsboard.mqtt.broker.actors.client.service.session.SessionClusterManager;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionChangesManager;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionCommandService;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class ClientActorContext {
    private final SessionClusterManager sessionClusterManager;
    private final SubscriptionChangesManager subscriptionChangesManager;
    private final SubscriptionCommandService subscriptionCommandService;
    private final ActorProcessor actorProcessor;
    private final ConnectService connectService;
    private final MqttMessageHandler mqttMessageHandler;
    private final ClientLogger clientLogger;
    private final StatsManager statsManager;

    @Value("${mqtt.pre-connect-queue.max-size:10000}")
    private int maxPreConnectQueueSize;
}
