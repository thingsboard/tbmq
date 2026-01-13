/**
 * Copyright © 2016-2026 The Thingsboard Authors
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

import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.config.UnauthorizedClientsProperties;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContinueResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFailure;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFinalResponse;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnauthorizedClientManagerImplTest {

    @Mock
    UnauthorizedClientService unauthorizedClientService;
    @Mock
    UnauthorizedClientsProperties properties;

    @Mock
    ClientActorState state;

    @Mock
    ClientSessionCtx sessionCtx;

    @Mock
    SessionInitMsg sessionInitMsg;

    @Mock
    EnhancedAuthContinueResponse enhancedAuthContinueResponse;

    @Mock
    EnhancedAuthFinalResponse enhancedAuthFinalResponse;

    @InjectMocks
    UnauthorizedClientManagerImpl unauthorizedClientManager;

    private final String username = "user";
    private final String reason = "auth failed";

    @BeforeEach
    void setup() {
        String clientId = "test-client";
        when(state.getClientId()).thenReturn(clientId);
        when(properties.isEnabled()).thenReturn(true);
    }

    @Test
    void testPersistClientUnauthorized_AuthResponse() {
        when(sessionInitMsg.getClientSessionCtx()).thenReturn(sessionCtx);
        when(sessionInitMsg.getUsername()).thenReturn(username);
        when(sessionInitMsg.getPasswordBytes()).thenReturn(new byte[]{1, 2, 3});
        when(unauthorizedClientService.save(any())).thenReturn(Futures.immediateVoidFuture());

        unauthorizedClientManager.persistClientUnauthorized(state, sessionInitMsg, reason);

        verify(unauthorizedClientService, times(1)).save(any(UnauthorizedClient.class));
    }

    @Test
    void testPersistClientUnauthorized_EnhancedAuthContinueResponse() {
        when(enhancedAuthContinueResponse.username()).thenReturn(username);
        when(enhancedAuthContinueResponse.enhancedAuthFailure()).thenReturn(EnhancedAuthFailure.CLIENT_RE_AUTH_MESSAGE_EVALUATION_ERROR);
        when(unauthorizedClientService.save(any())).thenReturn(Futures.immediateVoidFuture());

        unauthorizedClientManager.persistClientUnauthorized(state, sessionCtx, enhancedAuthContinueResponse);

        verify(unauthorizedClientService, times(1)).save(any(UnauthorizedClient.class));
    }

    @Test
    void testPersistClientUnauthorized_EnhancedAuthFinalResponse() {
        when(enhancedAuthFinalResponse.username()).thenReturn(username);
        when(enhancedAuthFinalResponse.enhancedAuthFailure()).thenReturn(EnhancedAuthFailure.CLIENT_FINAL_MESSAGE_EVALUATION_ERROR);
        when(unauthorizedClientService.save(any())).thenReturn(Futures.immediateVoidFuture());

        unauthorizedClientManager.persistClientUnauthorized(state, sessionCtx, enhancedAuthFinalResponse);

        verify(unauthorizedClientService, times(1)).save(any(UnauthorizedClient.class));
    }

    @Test
    void testPersistClientUnauthorized_Direct() {
        when(unauthorizedClientService.save(any())).thenReturn(Futures.immediateVoidFuture());

        unauthorizedClientManager.persistClientUnauthorized(state, sessionCtx, username, true, reason);

        verify(unauthorizedClientService, times(1)).save(any(UnauthorizedClient.class));
    }

    @Test
    void testRemoveClientUnauthorized() {
        when(unauthorizedClientService.remove(any())).thenReturn(Futures.immediateVoidFuture());

        unauthorizedClientManager.removeClientUnauthorized(state);

        verify(unauthorizedClientService, times(1)).remove(any(UnauthorizedClient.class));
    }
}
