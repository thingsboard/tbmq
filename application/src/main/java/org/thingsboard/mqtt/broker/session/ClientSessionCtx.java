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
package org.thingsboard.mqtt.broker.session;

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
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Getter
public class ClientSessionCtx implements SessionContext {

    private final UUID sessionId;
    private final SslHandler sslHandler;
    private final PubResponseProcessingCtx pubResponseProcessingCtx;
    private final MsgIdSequence msgIdSeq = new MsgIdSequence();
    private final AwaitingPubRelPacketsCtx awaitingPubRelPacketsCtx = new AwaitingPubRelPacketsCtx();
    private final ConcurrentMap<Integer, MqttPendingPublish> pendingPublishes = new ConcurrentHashMap<>();

    @Setter
    private volatile SessionInfo sessionInfo;
    @Setter
    private volatile List<AuthRulePatterns> authRulePatterns;
    @Setter
    private volatile ClientType clientType;
    @Setter
    private volatile MqttVersion mqttVersion;
    @Setter
    private volatile InetSocketAddress address;
    @Setter
    private volatile TopicAliasCtx topicAliasCtx;

    private ChannelHandlerContext channel;

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
        if (log.isDebugEnabled()) {
            log.debug("[{}] Closing channel...", getClientId());
        }
        this.channel.flush();
        this.channel.close();
        pendingPublishes.forEach((id, mqttPendingPublish) -> mqttPendingPublish.onChannelClosed());
        pendingPublishes.clear();
    }
}
