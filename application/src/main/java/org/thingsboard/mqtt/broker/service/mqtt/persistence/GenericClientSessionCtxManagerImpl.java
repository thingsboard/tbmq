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
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.GenericClientSessionCtx;
import org.thingsboard.mqtt.broker.dao.client.GenericClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.session.AwaitingPubRelPacketsCtx;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.MqttReasonCode;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import javax.annotation.PreDestroy;
import java.util.Collection;
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
        Set<Integer> awaitingQoS2PacketIds = getAwaitingQoS2PacketIds(clientSessionCtx);

        AwaitingPubRelPacketsCtx awaitingPubRelPacketsCtx = clientSessionCtx.getAwaitingPubRelPacketsCtx();
        awaitingPubRelPacketsCtx.loadPersistedPackets(awaitingQoS2PacketIds);

        MqttReasonCode code = MqttReasonCodeResolver.success(clientSessionCtx);
        awaitingQoS2PacketIds.forEach(packetId ->
                clientSessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createPubRecMsg(packetId, code)));
    }

    private Set<Integer> getAwaitingQoS2PacketIds(ClientSessionCtx clientSessionCtx) {
        return genericClientSessionCtxService.findGenericClientSessionCtx(clientSessionCtx.getClientId())
                .map(GenericClientSessionCtx::getQos2PublishPacketIds)
                .orElse(Collections.emptySet());
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
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Collection<ClientSessionCtx> allClientSessionCtx = getAllClientSessionCtx();
        List<GenericClientSessionCtx> allGenericCtxList = toGenericClientSessionCtxList(allClientSessionCtx);

        if (allGenericCtxList.isEmpty()) {
            log.info("No client contexts to save.");
            return;
        }

        saveAllGenericClientSessionCtx(allGenericCtxList);

        stopWatch.stop();
        log.info("Persisting client contexts took {} ms.", stopWatch.getTime());
    }

    private void saveAllGenericClientSessionCtx(List<GenericClientSessionCtx> genericCtxList) {
        log.info("Trying to save {} client contexts.", genericCtxList.size());
        try {
            genericClientSessionCtxService.saveAllGenericClientSessionCtx(genericCtxList);
        } catch (Exception e) {
            log.warn("Failed to save client contexts.", e);
        }
        if (log.isDebugEnabled()) {
            log.debug("Successfully saved client contexts.");
        }
    }

    private Collection<ClientSessionCtx> getAllClientSessionCtx() {
        return clientSessionCtxService.getAllClientSessionCtx();
    }

    private List<GenericClientSessionCtx> toGenericClientSessionCtxList(Collection<ClientSessionCtx> allClientSessionCtx) {
        return allClientSessionCtx.stream()
                .filter(clientSessionCtx -> clientSessionCtx.getSessionInfo().isPersistent())
                .map(this::toGenericClientSessionCtx)
                .collect(Collectors.toList());
    }

    private GenericClientSessionCtx toGenericClientSessionCtx(ClientSessionCtx ctx) {
        Set<Integer> qos2PublishPacketIds = getQos2PublishPacketIds(ctx);
        return buildGenericClientSessionCtx(ctx, qos2PublishPacketIds);
    }

    private GenericClientSessionCtx buildGenericClientSessionCtx(ClientSessionCtx ctx, Set<Integer> qos2PublishPacketIds) {
        return GenericClientSessionCtx.builder()
                .clientId(ctx.getClientId())
                .lastUpdatedTime(System.currentTimeMillis())
                .qos2PublishPacketIds(qos2PublishPacketIds)
                .build();
    }

    private Set<Integer> getQos2PublishPacketIds(ClientSessionCtx ctx) {
        return ctx.getAwaitingPubRelPacketsCtx().getAwaitingPackets().stream()
                .filter(AwaitingPubRelPacketsCtx.QoS2PubRelPacketInfo::isPersisted)
                .map(AwaitingPubRelPacketsCtx.QoS2PubRelPacketInfo::getPacketId)
                .collect(Collectors.toSet());
    }
}
