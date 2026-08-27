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
package org.thingsboard.mqtt.broker.session;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.SslHandler;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.actors.client.state.PubResponseProcessingCtx;
import org.thingsboard.mqtt.broker.actors.client.state.PublishedInFlightCtx;
import org.thingsboard.mqtt.broker.actors.client.state.PublishedInFlightCtxImpl;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.server.MqttHandlerCtx;
import org.thingsboard.mqtt.broker.service.auth.enhanced.ScramServerWithCallbackHandler;
import org.thingsboard.mqtt.broker.service.mqtt.delivery.MqttPublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.service.stats.FlowControlStats;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable per-connection state for a single MQTT client. One instance exists per client connection — over any
 * transport: plain TCP, TLS, WebSocket, or WebSocket Secure (see {@link #initializerName}) — created in
 * {@code MqttSessionHandler}; once the client is registered it is held as a value in
 * {@code ClientSessionCtxService} keyed by clientId.
 *
 * <p><b>Initialization phases.</b> Most fields are populated after construction, in stages and on two
 * threads, so accessing a not-yet-populated field can return {@code null} or throw. The main fields, grouped
 * by the thread that writes them:
 * <ul>
 *   <li><b>Netty I/O thread</b> — at construction: {@link #sessionId}, {@link #sslHandler},
 *       {@link #initializerName}; on the first inbound bytes: {@link #address} and {@link #channel}; while the
 *       CONNECT packet is parsed: {@link #mqttVersion} and, for MQTT 5 enhanced authentication, the
 *       {@link #enhancedAuthState} auth method and buffered CONNECT.</li>
 *   <li><b>Client actor thread</b> — while the CONNECT/AUTH exchange is processed: {@link #sessionInfo},
 *       {@link #authRulePatterns}, {@link #clientType}, the authentication results ({@link #username},
 *       {@link #authDetails}, {@link #clientCertCn}), the {@link #enhancedAuthState} SCRAM server, and
 *       {@link #publishedInFlightCtx}.</li>
 * </ul>
 * Accessors that dereference such a field (e.g. {@link #getClientId()}, {@link #isCleanSession()},
 * {@link #getAddressBytes()}, {@link #isWritable()}) are only safe once it has been set.
 *
 * <p><b>Concurrency.</b> Because these fields are written and read across the Netty I/O, client actor and
 * message-delivery threads, they are {@code volatile} (visibility only). Compound updates that need
 * atomicity use dedicated types (see {@link #nonWritableCounted}).
 */
@Slf4j
@Getter
@Setter
@ToString(of = "sessionId")
@EqualsAndHashCode(of = "sessionId")
public class ClientSessionCtx implements SessionContext {

    // Final: assigned once at construction and never reassigned.
    private final UUID sessionId;
    private final SslHandler sslHandler;
    private final String initializerName;
    private final PubResponseProcessingCtx pubResponseProcessingCtx;
    private final MsgIdSequence msgIdSeq = new MsgIdSequence();
    private final AwaitingPubRelPacketsCtx awaitingPubRelPacketsCtx = new AwaitingPubRelPacketsCtx();
    // Tracks whether this session is currently counted in the broker-wide nonWritableClientsCount gauge.
    // Used to make increment/decrement idempotent and to allow decrement on abrupt disconnect.
    private final AtomicBoolean nonWritableCounted = new AtomicBoolean(false);
    // Enhanced-auth (MQTT 5 SCRAM) handshake working set; partially cleared once it completes.
    private final EnhancedAuthState enhancedAuthState = new EnhancedAuthState();

    // Assigned after construction (see the "Initialization phases" above); volatile for cross-thread visibility.
    private volatile InetSocketAddress address;
    private volatile ChannelHandlerContext channel;
    private volatile SessionInfo sessionInfo;
    private volatile List<AuthRulePatterns> authRulePatterns;
    private volatile ClientType clientType;
    private volatile MqttVersion mqttVersion;
    private volatile TopicAliasCtx topicAliasCtx;
    private volatile PublishedInFlightCtx publishedInFlightCtx;
    private volatile String username;
    private volatile String authDetails;
    private volatile String clientCertCn;
    // Set when TBMQ itself initiates the channel close (see closeChannel()); read on the Netty I/O thread from
    // MqttSessionHandler.exceptionCaught() to tell a broker-side teardown apart from a spontaneous peer/network reset.
    private volatile boolean closeInitiated;

    public ClientSessionCtx() {
        this(null, UUID.randomUUID(), null, BrokerConstants.TCP);
    }

    public ClientSessionCtx(MqttHandlerCtx mqttHandlerCtx, UUID sessionId, SslHandler sslHandler, String initializerName) {
        this.sessionId = sessionId;
        this.sslHandler = sslHandler;
        this.initializerName = initializerName;
        this.pubResponseProcessingCtx = new PubResponseProcessingCtx(getMaxAwaitingQueueSize(mqttHandlerCtx));
    }

    public byte[] getAddressBytes() {
        return address.getAddress().getAddress();
    }

    public String getHostAddress() {
        return address.getAddress().getHostAddress();
    }

    private int getMaxAwaitingQueueSize(MqttHandlerCtx mqttHandlerCtx) {
        return mqttHandlerCtx == null ? BrokerConstants.MAX_IN_FLIGHT_MESSAGES : mqttHandlerCtx.getMaxInFlightMsgs();
    }

    public String getClientId() {
        return (sessionInfo != null && sessionInfo.getClientInfo() != null) ? sessionInfo.getClientId() : null;
    }

    // --- Enhanced-auth handshake: thin delegation; the state lives in EnhancedAuthState ---

    public String getAuthMethod() {
        return enhancedAuthState.getAuthMethod();
    }

    public void setAuthMethod(String authMethod) {
        enhancedAuthState.setAuthMethod(authMethod);
    }

    public ScramServerWithCallbackHandler getScramServerWithCallbackHandler() {
        return enhancedAuthState.getScramServer();
    }

    public void setScramServerWithCallbackHandler(ScramServerWithCallbackHandler scramServer) {
        enhancedAuthState.setScramServer(scramServer);
    }

    public MqttConnectMessage getConnectMsgFromEnhancedAuth() {
        return enhancedAuthState.getConnectMsg();
    }

    public void setConnectMsgFromEnhancedAuth(MqttConnectMessage connectMsg) {
        enhancedAuthState.setConnectMsg(connectMsg);
    }

    public boolean isDefaultAuth() {
        return enhancedAuthState.isDefaultAuth();
    }

    public void clearScramServer() {
        enhancedAuthState.clearScramServer();
    }

    public void clearConnectMsg() {
        enhancedAuthState.clearConnectMsg();
    }

    public void initPublishedInFlightCtx(FlowControlService flowControlService,
                                         MqttPublishMsgDeliveryService deliveryService,
                                         FlowControlStats stats,
                                         TbMessageStatsReportClient tbMessageStatsReportClient,
                                         int receiveMaxValue,
                                         int delayedQueueMaxSize) {
        publishedInFlightCtx = new PublishedInFlightCtxImpl(
                flowControlService, this, deliveryService, stats, tbMessageStatsReportClient, receiveMaxValue, delayedQueueMaxSize);
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

    public void onChannelWritable() {
        if (publishedInFlightCtx != null) {
            publishedInFlightCtx.onChannelWritable();
        }
    }

    // publishedInFlightCtx lifecycle (init/release) is driven solely by the client actor thread.
    public void releasePublishedInFlightCtx() {
        if (publishedInFlightCtx != null) {
            publishedInFlightCtx.release();
            publishedInFlightCtx = null;
        }
    }

    public boolean isWritable() {
        return channel.channel().isWritable();
    }

    public boolean isCleanSession() {
        return sessionInfo.isCleanSession();
    }

    public boolean isAppClient() {
        return ClientType.APPLICATION == clientType;
    }

    public void closeChannel() {
        log.debug("[{}] Closing channel...", getClientId());
        closeInitiated = true;
        channel.flush();
        channel.close();
    }
}
