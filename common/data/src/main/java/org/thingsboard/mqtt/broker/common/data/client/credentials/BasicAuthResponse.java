/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data.client.credentials;

import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;

@Data
public class BasicAuthResponse {

    private final MqttClientCredentials credentials;
    private final String errorMsg;

    public static BasicAuthResponse success(MqttClientCredentials credentials) {
        return new BasicAuthResponse(credentials, null);
    }

    public static BasicAuthResponse failure(String errorMsg) {
        return new BasicAuthResponse(null, errorMsg);
    }

    public boolean isSuccess() {
        return credentials != null;
    }

    public boolean isFailure() {
        return errorMsg != null;
    }
}
