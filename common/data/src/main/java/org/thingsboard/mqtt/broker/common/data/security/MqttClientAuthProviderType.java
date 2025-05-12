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
package org.thingsboard.mqtt.broker.common.data.security;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum MqttClientAuthProviderType {

    BASIC(0), SSL(1), JWT(2);

    private final int protoNumber;

    private static final Map<Integer, MqttClientAuthProviderType> LOOKUP_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(type -> type.protoNumber, Function.identity()));

    MqttClientAuthProviderType(int protoNumber) {
        this.protoNumber = protoNumber;
    }

    public static MqttClientAuthProviderType fromProtoNumber(int protoNumber) {
        return LOOKUP_MAP.get(protoNumber);
    }



}
