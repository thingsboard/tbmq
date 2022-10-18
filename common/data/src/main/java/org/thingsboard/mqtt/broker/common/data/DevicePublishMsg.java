/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data;

import io.netty.handler.codec.mqtt.MqttProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder(toBuilder = true)
@EqualsAndHashCode(exclude = "properties")
@AllArgsConstructor
public class DevicePublishMsg {
    private String clientId;
    private String topic;
    private Long serialNumber;
    private Long time; // TODO: use time to check if DEVICE queue is not overloaded (check latency)
    private Integer qos;
    private Integer packetId;
    private PersistedPacketType packetType;
    private byte[] payload;
    private MqttProperties properties;
    private boolean isRetained;
}
