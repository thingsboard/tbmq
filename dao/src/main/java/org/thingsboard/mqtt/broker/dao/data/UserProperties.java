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
package org.thingsboard.mqtt.broker.dao.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.handler.codec.mqtt.MqttProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProperties {

    @JsonProperty("props")
    private List<StringPair> values;

    public static MqttProperties mapToMqttProperties(UserProperties userProperties) {
        MqttProperties.UserProperties mqttUserProperties = mapToMqttUserProperties(userProperties);
        return userProperties != null ? createMqttProperties(mqttUserProperties) : MqttProperties.NO_PROPERTIES;
    }

    public static MqttProperties.UserProperties mapToMqttUserProperties(UserProperties userProperties) {
        return userProperties != null ? getMqttUserProperties(userProperties) : null;
    }

    private static MqttProperties createMqttProperties(MqttProperties.UserProperties userProperties) {
        MqttProperties mqttProperties = new MqttProperties();
        mqttProperties.add(userProperties);
        return mqttProperties;
    }

    private static MqttProperties.UserProperties getMqttUserProperties(UserProperties userProperties) {
        List<MqttProperties.StringPair> stringPairs = userProperties.getValues()
                .stream()
                .map(pair -> new MqttProperties.StringPair(pair.getKey(), pair.getValue()))
                .collect(Collectors.toList());
        return new MqttProperties.UserProperties(stringPairs);
    }

    public static UserProperties newInstance(MqttProperties mqttProperties) {
        MqttProperties.UserProperties userProperties = getUserProperties(mqttProperties);
        return userProperties != null ? getUserProperties(userProperties) : null;
    }

    private static MqttProperties.UserProperties getUserProperties(MqttProperties mqttProperties) {
        return (MqttProperties.UserProperties) mqttProperties.getProperty(MqttProperties.MqttPropertyType.USER_PROPERTY.value());
    }

    private static UserProperties getUserProperties(MqttProperties.UserProperties userProperties) {
        if (userProperties.value().isEmpty()) {
            return null;
        }
        List<StringPair> values = userProperties
                .value()
                .stream()
                .map(pair -> new StringPair(pair.key, pair.value))
                .collect(Collectors.toList());
        return new UserProperties(values);
    }

    @Data
    @EqualsAndHashCode
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class StringPair {
        @JsonProperty("k")
        private String key;
        @JsonProperty("v")
        private String value;
    }
}
