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
package org.thingsboard.mqtt.broker.service.auth.unauthorized;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.config.UnauthorizedClientsProperties;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContinueResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFinalResponse;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.UNKNOWN;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnauthorizedClientManagerImpl implements UnauthorizedClientManager {

    private final UnauthorizedClientService unauthorizedClientService;
    private final UnauthorizedClientsProperties props;

    @Override
    public void persistClientUnauthorized(ClientActorState state, SessionInitMsg sessionInitMsg, String reason) {
        persistClientUnauthorized(state, sessionInitMsg.getClientSessionCtx(), sessionInitMsg.getUsername(),
                sessionInitMsg.getPasswordBytes() != null, reason);
    }

    @Override
    public void persistClientUnauthorized(ClientActorState state, ClientSessionCtx clientSessionCtx, EnhancedAuthContinueResponse authResponse) {
        persistClientUnauthorized(state, clientSessionCtx, authResponse.username(),
                true, authResponse.enhancedAuthFailure().getReasonLog());
    }

    @Override
    public void persistClientUnauthorized(ClientActorState state, ClientSessionCtx clientSessionCtx, EnhancedAuthFinalResponse authResponse) {
        persistClientUnauthorized(state, clientSessionCtx, authResponse.username(),
                true, authResponse.enhancedAuthFailure().getReasonLog());
    }

    @Override
    public void persistClientUnauthorized(ClientActorState state, ClientSessionCtx clientSessionCtx,
                                          String username, boolean passwordProvided, String reason) {
        persist(
                state.getClientId(),
                BytesUtil.toHostAddress(clientSessionCtx.getAddressBytes()),
                username,
                passwordProvided,
                clientSessionCtx.getSslHandler() != null,
                reason
        );
    }

    @Override
    public void persistClientUnauthorized(String clientId, String reason) {
        persist(
                clientId,
                UNKNOWN, // can be improved later
                UNKNOWN,
                false,
                true,
                reason
        );
    }

    private void persist(String clientId, String ipAddress, String username, boolean passwordProvided, boolean tlsUsed, String reason) {
        if (!props.isEnabled()) {
            return;
        }
        UnauthorizedClient unauthorizedClient = UnauthorizedClient.builder()
                .clientId(clientId)
                .ipAddress(ipAddress)
                .ts(System.currentTimeMillis())
                .username(username)
                .passwordProvided(passwordProvided)
                .tlsUsed(tlsUsed)
                .reason(reason)
                .build();
        DonAsynchron.withCallback(unauthorizedClientService.save(unauthorizedClient),
                v -> log.debug("[{}] Unauthorized Client saved successfully! {}", unauthorizedClient.getClientId(), unauthorizedClient),
                throwable -> log.warn("[{}] Failed to persist unauthorized client! {}", unauthorizedClient.getClientId(), unauthorizedClient, throwable));
    }

    @Override
    public void removeClientUnauthorized(ClientActorState state) {
        if (!props.isEnabled()) {
            return;
        }
        UnauthorizedClient unauthorizedClient = UnauthorizedClient.withClientId(state.getClientId());
        DonAsynchron.withCallback(unauthorizedClientService.remove(unauthorizedClient),
                v -> log.debug("[{}] Unauthorized Client removed successfully!", state.getClientId()),
                throwable -> log.warn("[{}] Failed to removed unauthorized client!", state.getClientId(), throwable));
    }

}
