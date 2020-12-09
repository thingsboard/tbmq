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
package org.thingsboard.mqtt.broker.server;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.sevice.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.sevice.mqtt.MqttMessageHandlers;
import org.thingsboard.mqtt.broker.sevice.processing.PublishRetryService;
import org.thingsboard.mqtt.broker.sevice.processing.SuccessfulPublishService;
import org.thingsboard.mqtt.broker.sevice.subscription.SubscriptionService;

@Service
@AllArgsConstructor
public class MqttHandlerFactoryImpl implements MqttHandlerFactory {

    private final MqttMessageGenerator mqttMessageGenerator;
    private final MqttMessageHandlers messageHandlers;
    private final SubscriptionService subscriptionService;
    private final PublishRetryService retryService;
    private final SuccessfulPublishService successfulPublishService;

    @Override
    public MqttServerHandler create() {
        return new MqttServerHandler(mqttMessageGenerator, messageHandlers, subscriptionService, retryService, successfulPublishService);
    }
}
