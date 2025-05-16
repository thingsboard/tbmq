package org.thingsboard.mqtt.broker.service.auth.unauthorized;

import org.thingsboard.mqtt.broker.actors.client.messages.SessionInitMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthContinueResponse;
import org.thingsboard.mqtt.broker.service.auth.enhanced.EnhancedAuthFinalResponse;
import org.thingsboard.mqtt.broker.service.auth.providers.AuthResponse;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

public interface UnauthorizedClientManager {

    void persistClientUnauthorized(ClientActorState state, SessionInitMsg sessionInitMsg, AuthResponse authResponse);

    void persistClientUnauthorized(ClientActorState state, ClientSessionCtx clientSessionCtx, EnhancedAuthContinueResponse authResponse);

    void persistClientUnauthorized(ClientActorState state, ClientSessionCtx clientSessionCtx, EnhancedAuthFinalResponse authResponse);

    void persistClientUnauthorized(ClientActorState state, ClientSessionCtx clientSessionCtx,
                                   String username, boolean passwordProvided, String reason);

    void removeClientUnauthorized(ClientActorState state);

}
