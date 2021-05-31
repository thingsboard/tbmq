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
package org.thingsboard.mqtt.broker.actors.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.service.disconnect.DisconnectService;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.actors.client.util.ClientActorUtil;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActorProcessorImpl implements ActorProcessor {

    private final DisconnectService disconnectService;

    @Override
    public void onInit(ClientActorState state, SessionInitMsg sessionInitMsg) {
        ClientSessionCtx clientSessionCtx = sessionInitMsg.getClientSessionCtx();
        // TODO: pass more info on INIT to be able to auth client and check if we should connect it
        if (clientSessionCtx.getSessionId().equals(state.getCurrentSessionId())) {
            log.warn("[{}][{}] Trying to initialize the same session.", state.getClientId(), clientSessionCtx.getSessionId());
            if (state.getCurrentSessionState() != SessionState.DISCONNECTED) {
                state.updateSessionState(SessionState.DISCONNECTING);
                disconnectService.disconnect(state, new DisconnectReason(DisconnectReasonType.ON_ERROR, "Trying to init the same active session"));
            }
            return;
        }

        SessionState sessionState = state.getCurrentSessionState();
        if (sessionState != SessionState.DISCONNECTED) {
            // TODO: think if it's better to send DISCONNECT + INIT commands to actor instead (but need some limit logic to not got stuck in the loop)
            log.debug("[{}] Session was in {} state while Actor received INIT message, prev sessionId - {}, new sessionId - {}.",
                    state.getClientId(), sessionState, state.getCurrentSessionId(), clientSessionCtx.getSessionId());
            state.updateSessionState(SessionState.DISCONNECTING);
            disconnectService.disconnect(state, new DisconnectReason(DisconnectReasonType.ON_CONFLICTING_SESSIONS));
        }

        state.updateSessionState(SessionState.INITIALIZED);
        state.setClientSessionCtx(clientSessionCtx);
        state.clearStopActorCommandId();
    }

    @Override
    public void onDisconnect(ClientActorState state, DisconnectMsg disconnectMsg) {
        boolean isSessionValid = ClientActorUtil.validateAndLogSession(state, disconnectMsg);
        if (!isSessionValid) {
            return;
        }
        if (state.getCurrentSessionState() == SessionState.DISCONNECTED) {
            // TODO: duplicate DISCONNECTED event (if somehow prev session wasn't disconnected properly)
            log.debug("[{}][{}] Session is already disconnected.", state.getClientId(), state.getCurrentSessionId());
            return;
        }

        if (state.getCurrentSessionState() == SessionState.DISCONNECTING) {
            log.warn("[{}][{}] Session is in {} state. Will try to disconnect again.", state.getClientId(), state.getCurrentSessionId(), SessionState.DISCONNECTING);
        }

        state.updateSessionState(SessionState.DISCONNECTING);
        disconnectService.disconnect(state, disconnectMsg.getReason());
        state.updateSessionState(SessionState.DISCONNECTED);
    }
}
