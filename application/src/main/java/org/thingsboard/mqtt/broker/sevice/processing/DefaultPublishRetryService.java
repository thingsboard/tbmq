/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.sevice.processing;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DefaultPublishRetryService implements PublishRetryService {

    private final SuccessfulPublishService successfulPublishService;

    @Override
    public void registerPublishRetry(ChannelHandlerContext channel, MqttPublishMessage msg, String clientId, int packetId) {
        // TODO schedule resending of messages
    }

    @Override
    public void registerPubRec(String clientId, int packetId) {
        // TODO remove msg from schedule and make it successful

    }
}
