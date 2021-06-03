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
package org.thingsboard.mqtt.broker.actors.client.service.disconnect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;

@Slf4j
@Service
public class DisconnectServiceImpl implements DisconnectService {

    @Autowired
    private KeepAliveService keepAliveService;
    @Autowired
    private LastWillService lastWillService;
    @Autowired
    private ClientSessionCtxService clientSessionCtxService;
    @Autowired
    private MsgPersistenceManager msgPersistenceManager;
    @Autowired
    private ClientSessionEventService clientSessionEventService;

    @Override
    public void disconnect(ClientActorStateInfo actorState, DisconnectReason reason) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();

        if (sessionCtx.getSessionInfo() == null) {
            log.trace("[{}] Session wasn't fully initialized. Disconnect reason - {}.", sessionCtx.getSessionId(), reason);
            return;
        }

        log.trace("[{}][{}] Init client disconnection. Reason - {}.", sessionCtx.getClientId(), sessionCtx.getSessionId(), reason);

        // TODO: divide to 'local' and 'global' clearing ('global' should be done even if node is shutting down)
        try {
            clearClientSession(actorState, reason.getType());
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to clean client session. Reason - {}.", sessionCtx.getClientId(), sessionCtx.getSessionId(), e.getMessage());
            log.info("Detailed error: ", e);
            // TODO: think if we need to just leave it like this or throw exception
        }

        try {
            clientSessionEventService.disconnect(actorState.getCurrentSessionCtx().getSessionInfo().getClientInfo(), actorState.getCurrentSessionId());
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to notify client disconnected. Reason - {}.", sessionCtx.getClientId(), sessionCtx.getSessionId(), e.getMessage());
        }

        try {
            sessionCtx.getChannel().close();
        } catch (Exception e) {
            log.debug("[{}][{}] Failed to close channel. Reason - {}.", sessionCtx.getClientId(), sessionCtx.getSessionId(), e.getMessage());
        }

        log.info("[{}][{}] Client disconnected.", sessionCtx.getClientId(), sessionCtx.getSessionId());
    }

    private void clearClientSession(ClientActorStateInfo actorState, DisconnectReasonType disconnectReasonType) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        ClientInfo clientInfo = sessionCtx.getSessionInfo().getClientInfo();

        actorState.getQueuedMessages().clear();

        UUID sessionId = sessionCtx.getSessionId();
        keepAliveService.unregisterSession(sessionId);

        boolean sendLastWill = !DisconnectReasonType.ON_DISCONNECT_MSG.equals(disconnectReasonType);
        lastWillService.removeLastWill(sessionId, sendLastWill);

        if (sessionCtx.getSessionInfo().isPersistent()) {
            // TODO: group these methods (they should be only called together)
            msgPersistenceManager.stopProcessingPersistedMessages(clientInfo);
            msgPersistenceManager.saveAwaitingQoS2Packets(sessionCtx);
        }
        clientSessionCtxService.unregisterSession(clientInfo.getClientId());
    }
}
