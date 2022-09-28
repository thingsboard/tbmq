/**
 * Copyright © 2016-2022 The Thingsboard Authors
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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.SslHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.client.state.PubResponseProcessingCtx;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.retransmission.MqttPendingPublish;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private volatile List<AuthorizationRule> authorizationRules;
    @Getter
    @Setter
    private volatile ClientType clientType;
    @Getter
    @Setter
    private volatile MqttVersion mqttVersion;

    @Getter
    private ChannelHandlerContext channel;

    @Getter
    private final MsgIdSequence msgIdSeq = new MsgIdSequence();

    @Getter
    private final AwaitingPubRelPacketsCtx awaitingPubRelPacketsCtx = new AwaitingPubRelPacketsCtx();

    @Getter
    private final PubResponseProcessingCtx pubResponseProcessingCtx;

    @Getter
    private final ConcurrentMap<Integer, MqttPendingPublish> pendingPublishes = new ConcurrentHashMap<>();

    public ClientSessionCtx(UUID sessionId, SslHandler sslHandler, int maxInFlightMsgs) {
        this.sessionId = sessionId;
        this.sslHandler = sslHandler;
        this.pubResponseProcessingCtx = new PubResponseProcessingCtx(maxInFlightMsgs);
    }

    public void setChannel(ChannelHandlerContext channel) {
        this.channel = channel;
    }

    public String getClientId() {
        return (sessionInfo != null && sessionInfo.getClientInfo() != null) ?
                sessionInfo.getClientInfo().getClientId() : null;
    }

    public void closeChannel() {
        log.debug("[{}] Closing channel...", getClientId());
        ChannelFuture channelFuture = this.channel.close();
        channelFuture.addListener(future -> {
            pendingPublishes.forEach((id, mqttPendingPublish) -> mqttPendingPublish.onChannelClosed());
            pendingPublishes.clear();
        });
    }
}
