package org.thingsboard.mqtt.broker.service.auth.unauthorized;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.common.util.DonAsynchron;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientService;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContinueResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFinalResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnauthorizedClientManagerImpl implements UnauthorizedClientManager {

    private final UnauthorizedClientService unauthorizedClientService;

    @Override
    public void persistClientUnauthorized(ClientActorState state, SessionInitMsg sessionInitMsg, AuthResponse authResponse) {
        persistClientUnauthorized(state, sessionInitMsg.getClientSessionCtx(), sessionInitMsg.getUsername(),
                sessionInitMsg.getPasswordBytes() != null, authResponse.getReason());
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
        UnauthorizedClient unauthorizedClient = UnauthorizedClient.builder()
                .clientId(state.getClientId())
                .ipAddress(BytesUtil.toHostAddress(clientSessionCtx.getAddressBytes()))
                .ts(System.currentTimeMillis())
                .username(username)
                .passwordProvided(passwordProvided)
                .tlsUsed(clientSessionCtx.getSslHandler() != null)
                .reason(reason)
                .build();
        DonAsynchron.withCallback(unauthorizedClientService.save(unauthorizedClient),
                v -> log.debug("[{}] Unauthorized Client saved successfully! {}", state.getClientId(), unauthorizedClient),
                throwable -> log.warn("[{}] Failed to persist unauthorized client! {}", state.getClientId(), unauthorizedClient, throwable));
    }

    @Override
    public void removeClientUnauthorized(ClientActorState state) {
        UnauthorizedClient unauthorizedClient = UnauthorizedClient.builder()
                .clientId(state.getClientId())
                .build();
        DonAsynchron.withCallback(unauthorizedClientService.remove(unauthorizedClient),
                v -> log.debug("[{}] Unauthorized Client removed successfully!", state.getClientId()),
                throwable -> log.warn("[{}] Failed to removed unauthorized client!", state.getClientId(), throwable));
    }

}
