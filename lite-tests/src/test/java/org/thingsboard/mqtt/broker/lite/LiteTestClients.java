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
package org.thingsboard.mqtt.broker.lite;

/**
 * Releases the Paho clients a test created, whatever state the test left them in.
 */
final class LiteTestClients {

    private static final long DISCONNECT_TIMEOUT_MS = 1000;

    private LiteTestClients() {
    }

    static void closeQuietly(Object client) {
        try {
            if (client instanceof org.eclipse.paho.client.mqttv3.MqttClient v3) {
                if (v3.isConnected()) {
                    v3.disconnectForcibly(DISCONNECT_TIMEOUT_MS);
                }
                v3.close(true);
            } else if (client instanceof org.eclipse.paho.mqttv5.client.MqttClient v5) {
                if (v5.isConnected()) {
                    v5.disconnectForcibly(DISCONNECT_TIMEOUT_MS);
                }
                v5.close(true);
            } else if (client instanceof org.eclipse.paho.mqttv5.client.MqttAsyncClient v5Async) {
                if (v5Async.isConnected()) {
                    v5Async.disconnectForcibly(DISCONNECT_TIMEOUT_MS);
                }
                v5Async.close(true);
            }
        } catch (Exception ignored) {
            // the client is already gone; nothing is left to release
        }
    }

}
