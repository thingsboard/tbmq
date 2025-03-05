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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.handler.codec.mqtt.MqttReasonCodes;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

@Slf4j
@Service
@AllArgsConstructor
public class MqttPubRelHandler {

    private final MqttMessageGenerator mqttMessageGenerator;

    public void process(ClientSessionCtx ctx, int messageId) throws MqttException {
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Received PUBREL msg for packet {}.", ctx.getClientId(), ctx.getSessionId(), messageId);
        }

        var code = completePubRel(ctx, messageId);

        ctx.getChannel().writeAndFlush(mqttMessageGenerator.createPubCompMsg(messageId, code));
    }

    MqttReasonCodes.PubComp completePubRel(ClientSessionCtx ctx, int messageId) {
        boolean completed = ctx.getAwaitingPubRelPacketsCtx().complete(ctx.getClientId(), messageId);
        if (!completed) {
            if (log.isDebugEnabled()) {
                log.debug("[{}][{}] Couldn't find packetId {} for incoming PUBREL message.", ctx.getClientId(), ctx.getSessionId(), messageId);
            }
            return MqttReasonCodeResolver.packetIdNotFound(ctx);
        }
        return MqttReasonCodeResolver.pubCompSuccess(ctx);
    }

}
