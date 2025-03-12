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
package org.thingsboard.mqtt.broker.integration.service.integration.mqtt;

import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.MqttClientCallback;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;

@Slf4j
@Data
public class MqttIntegrationCallback implements MqttClientCallback {

    private final IntegrationLifecycleMsg lifecycleMsg;
    private final Consumer<Void> startProcessorSupplier;
    private final AtomicBoolean active = new AtomicBoolean(false);

    @Override
    public void connectionLost(Throwable cause) {
        log.info("[{}][{}] MQTT Integration lost connection to the target broker", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName());
        if (log.isDebugEnabled()) {
            log.debug("[{}][{}] MQTT integration connectionLost reason: ", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName(), cause);
        }
        active.set(false);
    }

    @Override
    public void onSuccessfulReconnect() {
        log.info("[{}][{}] MQTT Integration successfully reconnected to the target broker", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName());
        startProcessingIfNotStarted();
    }

    @Override
    public void onConnAck(MqttConnAckMessage connAckMessage) {
        log.info("[{}][{}] MQTT Integration established the connection to the target broker", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName());
        var returnCode = connAckMessage.variableHeader().connectReturnCode();
        log.info("[{}][{}] MQTT Integration received {} message from the target broker", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName(), returnCode);
        if (CONNECTION_ACCEPTED == returnCode) {
            startProcessingIfNotStarted();
        }
    }

    @Override
    public void onDisconnect(MqttMessage mqttDisconnectMessage) {
        log.info("[{}][{}] MQTT Integration received Disconnect packet from the target broker", lifecycleMsg.getIntegrationId(), lifecycleMsg.getName());
        active.set(false);
    }

    private void startProcessingIfNotStarted() {
        if (active.compareAndSet(false, true)) {
            startProcessorSupplier.accept(null);
        }
    }
}
