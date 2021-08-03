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
package org.thingsboard.mqtt.broker.session;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.client.state.RequestOrderCtx;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;

import java.util.UUID;

@Slf4j
public class ClientSessionCtx implements SessionContext {

    @Getter
    private final UUID sessionId;
    @Getter
    private final SslHandler sslHandler;
    @Getter
    @Setter
    private volatile SessionInfo sessionInfo;
    @Getter
    @Setter
    private volatile AuthorizationRule authorizationRule;

    @Getter
    private ChannelHandlerContext channel;

    @Getter
    private final MsgIdSequence msgIdSeq = new MsgIdSequence();

    @Getter
    private final IncomingMessagesCtx incomingMessagesCtx = new IncomingMessagesCtx();

    @Getter
    private final RequestOrderCtx requestOrderCtx;


    public ClientSessionCtx(UUID sessionId, SslHandler sslHandler, int maxInFlightMsgs) {
        this.sessionId = sessionId;
        this.sslHandler = sslHandler;
        this.requestOrderCtx = new RequestOrderCtx(maxInFlightMsgs);
    }

    public void setChannel(ChannelHandlerContext channel) {
        this.channel = channel;
    }

    public String getClientId() {
        return (sessionInfo != null && sessionInfo.getClientInfo() != null) ?
                sessionInfo.getClientInfo().getClientId() : null;
    }
}
