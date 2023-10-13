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
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Slf4j
@Service
@RequiredArgsConstructor
public class BasicDownLinkProcessorImpl implements BasicDownLinkProcessor {

    private final ClientSessionCtxService clientSessionCtxService;
    private final PublishMsgDeliveryService publishMsgDeliveryService;
    private final ClientLogger clientLogger;

    @Override
    public void process(String clientId, PublishMsgProto msg) {
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(clientId);
        if (clientSessionCtx == null) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] No client session on the node.", clientId);
            }
            return;
        }
        PublishMsg publishMsg = getPublishMsg(clientSessionCtx, msg, null);
        publishMsgDeliveryService.sendPublishMsgToClient(clientSessionCtx, publishMsg);
        clientLogger.logEvent(clientId, this.getClass(), "Delivered msg to basic client");
    }

    @Override
    public void process(Subscription subscription, PublishMsgProto msg) {
        ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(subscription.getClientId());
        if (clientSessionCtx == null) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] No client session on the node.", subscription.getClientId());
            }
            return;
        }
        PublishMsg publishMsg = getPublishMsg(clientSessionCtx, msg, subscription);
        publishMsgDeliveryService.sendPublishMsgToClient(clientSessionCtx, publishMsg);
        clientLogger.logEvent(subscription.getClientId(), this.getClass(), "Delivered msg to basic client");
    }

    private PublishMsg getPublishMsg(ClientSessionCtx clientSessionCtx, PublishMsgProto msg, Subscription subscription) {
        return PublishMsg.builder()
                .packetId(clientSessionCtx.getMsgIdSeq().nextMsgId())
                .topicName(msg.getTopicName())
                .payload(msg.getPayload().toByteArray())
                .qosLevel(subscription == null ? msg.getQos() : Math.min(subscription.getQos(), msg.getQos()))
                .isRetained(subscription == null ? msg.getRetain() : subscription.getOptions().isRetain(msg))
                .isDup(false)
                .properties(ProtoConverter.createMqttProperties(msg.getUserPropertiesList()))
                .build();
    }

}
