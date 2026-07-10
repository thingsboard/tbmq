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
package org.thingsboard.mqtt.broker.service.stats;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;

public class StubConnectionStats implements ConnectionStats {

    public static final StubConnectionStats STUB_CONNECTION_STATS = new StubConnectionStats();

    @Override
    public void onConnectionAccepted() {
    }

    @Override
    public void onConnectionRefused(MqttConnectReturnCode returnCode) {
    }

    @Override
    public void onConnectionError() {
    }

    @Override
    public int getAcceptedCount() {
        return 0;
    }

    @Override
    public int getRefusedCount() {
        return 0;
    }

    @Override
    public int getErrorCount() {
        return 0;
    }

    @Override
    public void reset() {
    }
}
