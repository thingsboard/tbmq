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
package org.thingsboard.mqtt.broker.service.processing.downlink.basic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicDownLinkProcessorImpl implements BasicDownLinkProcessor {

    private final ClientSessionCtxService clientSessionCtxService;
    private final ClientMqttActorManager clientMqttActorManager;
    private final PublishMsgDeliveryService publishMsgDeliveryService;

    @Override
    public void process(String clientId, QueueProtos.PublishMsgProto msg) {
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(clientId);
        if (clientSessionCtx == null) {
            log.trace("[{}] No client session on the node.", clientId);
            return;
        }
        try {
            publishMsgDeliveryService.sendPublishMsgToClient(clientSessionCtx, clientSessionCtx.getMsgIdSeq().nextMsgId(),
                    msg.getTopicName(), msg.getQos(), false, msg.getPayload().toByteArray());
        } catch (Exception e) {
            log.debug("[{}] Failed to deliver msg to client. Exception - {}, reason - {}.", clientId, e.getClass().getSimpleName(), e.getMessage());
            log.trace("Detailed error: ", e);
            clientMqttActorManager.disconnect(clientId, clientSessionCtx.getSessionId(), new DisconnectReason(DisconnectReasonType.ON_ERROR,
                    "Failed to deliver PUBLISH msg"));
        }
    }

}
