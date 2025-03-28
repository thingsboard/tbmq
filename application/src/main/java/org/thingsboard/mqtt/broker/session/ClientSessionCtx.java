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
package org.thingsboard.mqtt.broker.session;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.SslHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.actors.client.state.PubResponseProcessingCtx;
import org.thingsboard.mqtt.broker.actors.client.state.PublishedInFlightCtx;
import org.thingsboard.mqtt.broker.actors.client.state.PublishedInFlightCtxImpl;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.server.MqttHandlerCtx;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramServerWithCallbackHandler;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
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

    private final MqttHandlerCtx mqttHandlerCtx;
    private final UUID sessionId;
    private final SslHandler sslHandler;
    private final String initializerName;
    private final PubResponseProcessingCtx pubResponseProcessingCtx;
    private final ConcurrentMap<Integer, MqttPendingPublish> pendingPublishes;
    private final MsgIdSequence msgIdSeq = new MsgIdSequence();
    private final AwaitingPubRelPacketsCtx awaitingPubRelPacketsCtx = new AwaitingPubRelPacketsCtx();

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
    @Setter
    private volatile PublishedInFlightCtx publishedInFlightCtx;
    @Setter
    private volatile MqttConnectMessage connectMsgFromEnhancedAuth;
    @Setter
    private volatile String authMethod;
    @Setter
    private volatile ScramServerWithCallbackHandler scramServerWithCallbackHandler;

    @Setter
    private ChannelHandlerContext channel;

    public ClientSessionCtx() {
        this(null, UUID.randomUUID(), null, BrokerConstants.TCP);
    }

    public ClientSessionCtx(MqttHandlerCtx mqttHandlerCtx, UUID sessionId, SslHandler sslHandler, String initializerName) {
        this.mqttHandlerCtx = mqttHandlerCtx;
        this.sessionId = sessionId;
        this.sslHandler = sslHandler;
        this.initializerName = initializerName;
        this.pubResponseProcessingCtx = new PubResponseProcessingCtx(getMaxAwaitingQueueSize(mqttHandlerCtx));
        this.pendingPublishes = initPendingPublishes(mqttHandlerCtx);
    }

    public byte[] getAddressBytes() {
        return address.getAddress().getAddress();
    }

    private int getMaxAwaitingQueueSize(MqttHandlerCtx mqttHandlerCtx) {
        return mqttHandlerCtx == null ? BrokerConstants.MAX_IN_FLIGHT_MESSAGES : mqttHandlerCtx.getMaxInFlightMsgs();
    }

    private ConcurrentMap<Integer, MqttPendingPublish> initPendingPublishes(MqttHandlerCtx mqttHandlerCtx) {
        if (mqttHandlerCtx == null) {
            return null;
        }
        return mqttHandlerCtx.isRetransmissionEnabled() ? new ConcurrentHashMap<>() : null;
    }

    public String getClientId() {
        return (sessionInfo != null && sessionInfo.getClientInfo() != null) ? sessionInfo.getClientId() : null;
    }

    public void initPublishedInFlightCtx(FlowControlService flowControlService, ClientSessionCtx sessionCtx, int receiveMaxValue, int delayedQueueMaxSize) {
        publishedInFlightCtx = new PublishedInFlightCtxImpl(flowControlService, sessionCtx, receiveMaxValue, delayedQueueMaxSize);
    }

    public boolean addInFlightMsg(MqttPublishMessage mqttPubMsg) {
        if (publishedInFlightCtx != null) {
            return publishedInFlightCtx.addInFlightMsg(mqttPubMsg);
        }
        return true;
    }

    public void ackInFlightMsg(int msgId) {
        if (publishedInFlightCtx != null) {
            publishedInFlightCtx.ackInFlightMsg(msgId);
        }
    }

    public boolean isWritable() {
        return channel.channel().isWritable();
    }

    public void closeChannel() {
        log.debug("[{}] Closing channel...", getClientId());
        channel.flush();
        channel.close();
        if (!CollectionUtils.isEmpty(pendingPublishes)) {
            pendingPublishes.forEach((id, mqttPendingPublish) -> mqttPendingPublish.onChannelClosed());
            pendingPublishes.clear();
        }
    }

    public boolean isDefaultAuth() {
        return connectMsgFromEnhancedAuth == null;
    }

    public void clearScramServer() {
        scramServerWithCallbackHandler = null;
    }

    public void clearConnectMsg() {
        connectMsgFromEnhancedAuth = null;
    }
}
