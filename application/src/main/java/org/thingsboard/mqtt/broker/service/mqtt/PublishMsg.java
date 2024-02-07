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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@Builder(toBuilder = true)
@ToString
public class PublishMsg {

    private final int packetId;
    private final String topicName;
    private final byte[] payload;
    private final ByteBuf byteBuf;
    private final int qosLevel;
    private final boolean isRetained;
    private final boolean isDup;
    private final MqttProperties properties;

    public PublishMsg(int packetId, String topicName, byte[] payload, int qosLevel, boolean isRetained, boolean isDup) {
        this.packetId = packetId;
        this.topicName = topicName;
        this.payload = payload;
        this.byteBuf = Unpooled.wrappedBuffer(payload);
        this.qosLevel = qosLevel;
        this.isRetained = isRetained;
        this.isDup = isDup;
        this.properties = MqttProperties.NO_PROPERTIES;
    }

}
