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
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.PacketIdAndOffset;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ClientSessionCtx implements SessionContext {

    @Getter
    private final UUID sessionId;
    @Getter
    @Setter
    private volatile SessionInfo sessionInfo;
    @Getter
    @Setter
    private volatile AuthorizationRule authorizationRule;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean cleared = new AtomicBoolean(false);

    @Getter
    private ChannelHandlerContext channel;

    private final AtomicInteger msgIdSeq = new AtomicInteger(1);

    public ClientSessionCtx(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public void setChannel(ChannelHandlerContext channel) {
        this.channel = channel;
    }

    public int nextMsgId() {
        synchronized (this.msgIdSeq) {
            this.msgIdSeq.compareAndSet(0xffff, 1);
            return this.msgIdSeq.getAndIncrement();
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public void setDisconnected() {
        this.connected.getAndSet(false);
    }

    public void setConnected() {
        this.connected.getAndSet(true);
    }

    public boolean tryClearState() {
        return this.cleared.getAndSet(true);
    }

    public String getClientId() {
        return (sessionInfo != null && sessionInfo.getClientInfo() != null) ?
                sessionInfo.getClientInfo().getClientId() : null;
    }
}
