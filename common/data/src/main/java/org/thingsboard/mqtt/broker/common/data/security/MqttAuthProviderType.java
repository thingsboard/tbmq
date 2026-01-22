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
package org.thingsboard.mqtt.broker.common.data.security;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum MqttAuthProviderType {

    MQTT_BASIC(0, "MQTT Basic"),
    X_509(1, "X.509 Certificate chain"),
    JWT(2, "JWT"),
    SCRAM(3, "SCRAM"),
    HTTP(4, "HTTP Service");

    private final int protoNumber;
    private final String displayName;

    private static final Map<Integer, MqttAuthProviderType> LOOKUP_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(type -> type.protoNumber, Function.identity()));

    MqttAuthProviderType(int protoNumber, String displayName) {
        this.protoNumber = protoNumber;
        this.displayName = displayName;
    }

    public static MqttAuthProviderType fromProtoNumber(int protoNumber) {
        return LOOKUP_MAP.get(protoNumber);
    }

    public static final List<MqttAuthProviderType> defaultPriorityList = Arrays.stream(MqttAuthProviderType.values())
            .filter(type -> type != MqttAuthProviderType.SCRAM)
            .toList();

    public static boolean isBuiltInAuthProvider(String name) {
        return MqttAuthProviderType.SCRAM.name().equals(name)
                || MqttAuthProviderType.JWT.name().equals(name)
                || MqttAuthProviderType.HTTP.name().equals(name);
    }
}
