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
package org.thingsboard.mqtt.broker.service.mqtt.client.event.data;

import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.Data;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME;

@Data
public class ClientConnectInfo {

    private final String mqttVersion;
    private final String authDetails;
    private final boolean connectOnConflict;

    public static ClientConnectInfo fromCtx(ClientSessionCtx ctx, boolean connectOnConflict) {
        return new ClientConnectInfo(ctx.getMqttVersion().name(), ctx.getAuthDetails(), connectOnConflict);
    }

    public static ClientConnectInfo defaultInfo() {
        return new ClientConnectInfo(MqttVersion.MQTT_3_1_1.name(), WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME, false);
    }

    public static ClientConnectInfo emptyInfo() {
        return new ClientConnectInfo(null, null, false);
    }

}
