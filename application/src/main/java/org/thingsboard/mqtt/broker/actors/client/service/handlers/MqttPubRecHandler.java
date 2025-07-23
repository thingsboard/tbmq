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
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPubRecMsg;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Service
@AllArgsConstructor
@Slf4j
public class MqttPubRecHandler {

    private final MsgPersistenceManager msgPersistenceManager;
    private final PublishMsgDeliveryService publishMsgDeliveryService;

    public void process(ClientSessionCtx ctx, MqttPubRecMsg msg) throws MqttException {
        int messageId = msg.getMessageId();
        if (log.isTraceEnabled()) {
            log.trace("[{}][{}] Received PUBREC msg for packet {}.", ctx.getClientId(), ctx.getSessionId(), messageId);
        }

        if (reasonCodeFailure(msg)) {
            ctx.ackInFlightMsg(messageId);
            if (ctx.getSessionInfo().isPersistent()) {
                msgPersistenceManager.processPubRecNoPubRelDelivery(ctx, messageId);
            }
            return;
        }

        if (ctx.getSessionInfo().isPersistent()) {
            msgPersistenceManager.processPubRec(ctx, messageId);
        } else {
            if (!ctx.isWritable()) {
                log.debug("[{}] Channel is not writable. Skip send PubRel {}", ctx.getClientId(), messageId);
                return;
            }
            publishMsgDeliveryService.sendPubRelMsgToClient(ctx, messageId);
        }
    }

    boolean reasonCodeFailure(MqttPubRecMsg msg) {
        return Byte.toUnsignedInt(msg.getReasonCode().byteValue()) >= Byte.toUnsignedInt(MqttReasonCodes.PubRec.UNSPECIFIED_ERROR.byteValue());
    }

}
