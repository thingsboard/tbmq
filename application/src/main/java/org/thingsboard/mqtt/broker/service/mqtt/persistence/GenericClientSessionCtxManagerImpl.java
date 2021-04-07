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
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.dao.client.GenericClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.IncomingMessagesCtx;

import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class GenericClientSessionCtxManagerImpl implements GenericClientSessionCtxManager {
    private final GenericClientSessionCtxService genericClientSessionCtxService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final MqttMessageGenerator mqttMessageGenerator;

    @Override
    public void resendPersistedPubRelMessages(ClientSessionCtx clientSessionCtx) {
        Set<Integer> awaitingQoS2PacketIds = genericClientSessionCtxService.findGenericClientSessionCtx(clientSessionCtx.getClientId())
                .map(GenericClientSessionCtx::getQos2PublishPacketIds)
                .orElse(Collections.emptySet());
        IncomingMessagesCtx incomingMessagesCtx = clientSessionCtx.getIncomingMessagesCtx();
        incomingMessagesCtx.setAwaitingPacketIds(awaitingQoS2PacketIds);
        for (Integer packetId : awaitingQoS2PacketIds) {
            clientSessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createPubRecMsg(packetId));
        }
    }

    @Override
    public void processIncomingPublish(int packetId, ClientSessionCtx ctx) {
        ctx.getIncomingMessagesCtx().await(packetId);
    }

    @Override
    public void processPubRel(int packetId, ClientSessionCtx ctx) {
        boolean completed = ctx.getIncomingMessagesCtx().complete(packetId);
        if (!completed) {
            log.debug("[{}][{}] Couldn't find packetId {} for incoming PUBREL message.", ctx.getClientId(), ctx.getSessionId(), packetId);
        }
    }

    @Override
    public void saveAwaitingQoS2Packets(ClientSessionCtx ctx) {
        GenericClientSessionCtx genericClientSessionCtx = toGenericClientSessionCtx(ctx);
        genericClientSessionCtxService.saveGenericClientSessionCtx(genericClientSessionCtx);
    }

    @Override
    public void clearAwaitingQoS2Packets(String clientId) {
        genericClientSessionCtxService.deleteGenericClientSessionCtx(clientId);
    }

    @PreDestroy
    public void destroy() {
        List<GenericClientSessionCtx> genericCtxList = clientSessionCtxService.getAllClientSessionCtx().stream()
                .filter(clientSessionCtx -> clientSessionCtx.getSessionInfo().isPersistent())
                .map(this::toGenericClientSessionCtx)
                .collect(Collectors.toList());
        log.info("Trying to save {} client contexts.", genericCtxList.size());
        try {
            genericClientSessionCtxService.saveAllGenericClientSessionCtx(genericCtxList);
            log.info("Successfully saved client contexts.");
        } catch (Exception e) {
            log.warn("Failed to save client contexts. Reason: {}.", e.getMessage());
            log.trace("Detailed error: ", e);
        }
    }

    private GenericClientSessionCtx toGenericClientSessionCtx(ClientSessionCtx ctx) {
        return GenericClientSessionCtx.builder()
                .clientId(ctx.getClientId())
                .qos2PublishPacketIds(ctx.getIncomingMessagesCtx().getAwaitingPacketIds())
                .build();
    }
}
