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
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.ssl.SslHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class ClientSessionCtx implements SessionContext {

    @Getter
    private final UUID sessionId;
    @Getter
    private final SslHandler sslHandler;
    // Locking netty and connection handler to switch from processing msgs from queue - to processing in netty threads
    @Getter
    private final ReentrantLock connectionLock = new ReentrantLock();
    // Locking msg process and disconnect to prevent race condition
    @Getter
    private final ReentrantLock processingLock = new ReentrantLock();
    @Getter
    @Setter
    private volatile SessionInfo sessionInfo;
    @Getter
    @Setter
    private volatile AuthorizationRule authorizationRule;

    @Getter
    private final UnprocessedMessagesQueue unprocessedMessagesQueue = new UnprocessedMessagesQueue();

    private final AtomicReference<SessionState> sessionState = new AtomicReference<>(SessionState.CREATED);

    @Getter
    private ChannelHandlerContext channel;

    @Getter
    private final MsgIdSequence msgIdSeq = new MsgIdSequence();


    public ClientSessionCtx(UUID sessionId, SslHandler sslHandler) {
        this.sessionId = sessionId;
        this.sslHandler = sslHandler;
    }

    public void setChannel(ChannelHandlerContext channel) {
        this.channel = channel;
    }

    public SessionState getSessionState() {
        return sessionState.get();
    }

    public SessionState updateSessionState(SessionState newState) {
        return sessionState.getAndSet(newState);
    }

    public String getClientId() {
        return (sessionInfo != null && sessionInfo.getClientInfo() != null) ?
                sessionInfo.getClientInfo().getClientId() : null;
    }
}
