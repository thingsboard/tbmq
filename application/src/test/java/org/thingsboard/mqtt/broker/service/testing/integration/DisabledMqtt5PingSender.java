/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.testing.integration;


import org.eclipse.paho.mqttv5.client.MqttPingSender;
import org.eclipse.paho.mqttv5.client.internal.ClientComms;

public class DisabledMqtt5PingSender implements MqttPingSender {

    public static final DisabledMqtt5PingSender DISABLED_MQTT_PING_SENDER = new DisabledMqtt5PingSender();

    private DisabledMqtt5PingSender() {
    }

    @Override
    public void init(ClientComms comms) {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void schedule(long delayInMilliseconds) {
    }
}
