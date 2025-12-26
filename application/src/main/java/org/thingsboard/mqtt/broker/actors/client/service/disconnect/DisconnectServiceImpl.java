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
package org.thingsboard.mqtt.broker.actors.client.service.disconnect;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisconnectServiceImpl implements DisconnectService {

    private final KeepAliveService keepAliveService;
    private final LastWillService lastWillService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final MsgPersistenceManager msgPersistenceManager;
    private final ClientSessionEventService clientSessionEventService;
    private final RateLimitService rateLimitService;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final AuthorizationRuleService authorizationRuleService;
    private final FlowControlService flowControlService;
    private final RateLimitCacheService rateLimitCacheService;
    private final TbMessageStatsReportClient tbMessageStatsReportClient;

    @Override
    public void disconnect(ClientActorStateInfo actorState, MqttDisconnectMsg disconnectMsg) {
        DisconnectReason reason = disconnectMsg.getReason();
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();

        if (sessionCtx.getSessionInfo() == null) {
            log.trace("[{}] Session wasn't fully initialized. Disconnect reason - {}.", sessionCtx.getSessionId(), reason);
            closeChannel(sessionCtx);
            return;
        }

        log.debug("[{}][{}][{}] Init client disconnection. Reason - {}.", sessionCtx.getAddress(), sessionCtx.getClientId(), sessionCtx.getSessionId(), reason);

        if (shouldSendServerDisconnect(sessionCtx, reason)) {
            MqttReasonCodes.Disconnect code = MqttReasonCodeResolver.disconnect(reason.getType());
            sessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createDisconnectMsg(code));
        }

        var sessionExpiryInterval = getSessionExpiryInterval(disconnectMsg.getProperties());
        notifyClientDisconnected(actorState, sessionExpiryInterval);
        cleanupClientSession(actorState, disconnectMsg, sessionExpiryInterval);
    }

    private int getSessionExpiryInterval(MqttProperties properties) {
        return MqttPropertiesUtil.getDisconnectSessionExpiryIntervalValue(properties);
    }

    /**
     * Server-initiated DISCONNECT is supported only for MQTT 5 clients.
     * We send DISCONNECT only when:
     * - the connection was established successfully,
     * - the client did not explicitly send DISCONNECT,
     * - and we are not disconnecting due to channel close.
     */
    private boolean shouldSendServerDisconnect(ClientSessionCtx sessionCtx, DisconnectReason reason) {
        return MqttVersion.MQTT_5 == sessionCtx.getMqttVersion() && reason.getType().allowsServerDisconnect();
    }

    void closeChannel(ClientSessionCtx sessionCtx) {
        try {
            sessionCtx.closeChannel();
        } catch (Exception e) {
            log.debug("[{}][{}] Failed to close channel.", sessionCtx.getClientId(), sessionCtx.getSessionId(), e);
        }
    }

    void notifyClientDisconnected(ClientActorStateInfo actorState, int sessionExpiryInterval) {
        log.trace("[{}] Executing notifyClientDisconnected", actorState.getClientId());
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        try {
            SessionInfo disconnectSessionInfo = sessionCtx.getSessionInfo().withSessionExpiryInterval(sessionExpiryInterval);
            clientSessionEventService.notifyClientDisconnected(disconnectSessionInfo, null);
        } catch (Exception e) {
            log.warn("[{}][{}][{}] Failed to notify client disconnected.",
                    sessionCtx.getClientId(), sessionCtx.getSessionId(), sessionExpiryInterval, e);
        }
    }

    void cleanupClientSession(ClientActorStateInfo actorState, MqttDisconnectMsg disconnectMsg, int sessionExpiryInterval) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        ClientInfo clientInfo = sessionCtx.getSessionInfo().getClientInfo();
        var disconnectReasonType = disconnectMsg.getReason().getType();

        actorState.getQueuedMessages().clear();

        if (sessionCtx.getSessionInfo().isPersistent()) {
            processPersistentDisconnect(sessionCtx, clientInfo);
        } else {
            if (disconnectReasonType.isNotConflictingSession()) {
                rateLimitCacheService.decrementSessionCount();
            }
        }

        clientSessionCtxService.unregisterSession(clientInfo.getClientId());
        keepAliveService.unregisterSession(sessionCtx.getSessionId());
        lastWillService.removeAndExecuteLastWillIfNeeded(disconnectMsg, sessionExpiryInterval);

        rateLimitService.remove(sessionCtx.getClientId());
        authorizationRuleService.evict(sessionCtx.getClientId());
        flowControlService.removeFromMap(sessionCtx.getClientId());
        tbMessageStatsReportClient.removeClient(sessionCtx.getClientId());
        closeChannel(sessionCtx);

        log.debug("[{}][{}] Client disconnected", sessionCtx.getClientId(), sessionCtx.getSessionId());
    }

    void processPersistentDisconnect(ClientSessionCtx sessionCtx, ClientInfo clientInfo) {
        try {
            msgPersistenceManager.stopProcessingPersistedMessages(clientInfo);
            msgPersistenceManager.saveAwaitingQoS2Packets(sessionCtx);
        } catch (Exception e) {
            log.warn("[{}] Failed to stop processing persisted messages and save QoS 2 packets", clientInfo.getClientId(), e);
        }
    }

}
