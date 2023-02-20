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
package org.thingsboard.mqtt.broker.service.processing.downlink.basic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
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
    private final ClientLogger clientLogger;

    @Override
    public void process(String clientId, QueueProtos.PublishMsgProto msg) {
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(clientId);
        if (clientSessionCtx == null) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] No client session on the node.", clientId);
            }
            return;
        }
        try {
            PublishMsg publishMsg = getPublishMsg(clientSessionCtx, msg);
            publishMsgDeliveryService.sendPublishMsgToClient(clientSessionCtx, publishMsg);
            clientLogger.logEvent(clientId, this.getClass(), "Delivered msg to basic client");
        } catch (Exception e) {
            log.warn("[{}] Failed to deliver msg to client.", clientId, e);
            disconnect(clientId, clientSessionCtx);
        }
    }

    private void disconnect(String clientId, ClientSessionCtx clientSessionCtx) {
        clientMqttActorManager.disconnect(
                clientId,
                new MqttDisconnectMsg(
                        clientSessionCtx.getSessionId(),
                        new DisconnectReason(DisconnectReasonType.ON_ERROR, "Failed to deliver PUBLISH msg")));
    }

    private PublishMsg getPublishMsg(ClientSessionCtx clientSessionCtx, QueueProtos.PublishMsgProto msg) {
        return PublishMsg.builder()
                .packetId(clientSessionCtx.getMsgIdSeq().nextMsgId())
                .topicName(msg.getTopicName())
                .payload(msg.getPayload().toByteArray())
                .qosLevel(msg.getQos())
                .isRetained(msg.getRetain())
                .isDup(false)
                .properties(ProtoConverter.createMqttProperties(msg.getUserPropertiesList()))
                .build();
    }

}
