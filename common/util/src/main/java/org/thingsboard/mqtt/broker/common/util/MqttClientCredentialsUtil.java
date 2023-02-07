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
package org.thingsboard.mqtt.broker.common.util;

import org.thingsboard.mqtt.broker.common.data.dto.ShortMqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;

public class MqttClientCredentialsUtil {

    public static <T> T getMqttCredentials(MqttClientCredentials mqttClientCredentials, Class<T> credentialsClassType) {
        T credentials = JacksonUtil.fromString(mqttClientCredentials.getCredentialsValue(), credentialsClassType);
        if (credentials == null) {
            throw new IllegalArgumentException("Invalid credentials body for mqtt credentials!");
        }
        return credentials;
    }

    public static ShortMqttClientCredentials toShortMqttClientCredentials(MqttClientCredentials mqttClientCredentials) {
        return ShortMqttClientCredentials.builder()
                .id(mqttClientCredentials.getId())
                .name(mqttClientCredentials.getName())
                .clientType(mqttClientCredentials.getClientType())
                .credentialsType(mqttClientCredentials.getCredentialsType())
                .createdTime(mqttClientCredentials.getCreatedTime())
                .build();
    }
}
