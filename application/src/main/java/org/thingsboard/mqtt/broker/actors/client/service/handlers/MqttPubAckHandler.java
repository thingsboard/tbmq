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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.retransmission.RetransmissionService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Service
@AllArgsConstructor
@Slf4j
public class MqttPubAckHandler {

    private final MsgPersistenceManager msgPersistenceManager;
    private final RetransmissionService retransmissionService;
    private final boolean isTraceEnabled = log.isTraceEnabled();

    public void process(ClientSessionCtx ctx, int messageId) throws MqttException {
        if (isTraceEnabled) {
            log.trace("[{}][{}] Received PUBACK msg for packet {}.", ctx.getClientId(), ctx.getSessionId(), messageId);
        }
        if (ctx.getSessionInfo().isPersistent()) {
            msgPersistenceManager.processPubAck(ctx, messageId);
        }
        retransmissionService.onPubAckReceived(ctx, messageId);
    }
}
