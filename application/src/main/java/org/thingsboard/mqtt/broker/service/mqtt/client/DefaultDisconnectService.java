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
package org.thingsboard.mqtt.broker.service.mqtt.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;

import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDisconnectService implements DisconnectService {
    private final ExecutorService disconnectExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("disconnect-executor"));

    private final KeepAliveService keepAliveService;
    private final LastWillService lastWillService;
    private final SubscriptionManager subscriptionManager;
    private final ClientSessionManager clientSessionManager;

    @Value("${application.disconnect.executor-shutdown-timout-ms:500}")
    private long shutdownTimeout;

    @Override
    public void disconnect(ClientSessionCtx sessionCtx, DisconnectReason reason) {
        disconnect(sessionCtx, reason, true);
    }

    @Override
    public void disconnect(ClientSessionCtx sessionCtx, DisconnectReason reason, boolean needChannelClose) {
        boolean isStateCleared = sessionCtx.tryClearState();
        if (isStateCleared) {
            return;
        }
        sessionCtx.setDisconnected();
        UUID sessionId = sessionCtx.getSessionId();
        log.trace("[{}][{}] Init client disconnection.", sessionCtx.getClientId(), sessionId);
        disconnectExecutor.execute(() -> {
            // TODO: think about adding timeout
            sessionCtx.getLock().lock();
            try {
                boolean sendLastWill = !DisconnectReason.ON_DISCONNECT_MSG.equals(reason);
                lastWillService.removeLastWill(sessionId, sendLastWill);
                keepAliveService.unregisterSession(sessionId);
                ClientInfo clientInfo = getClientInfo(sessionCtx.getSessionInfo());
                if (clientInfo != null) {
                    if (!sessionCtx.getSessionInfo().isPersistent()) {
                        subscriptionManager.clearSubscriptions(clientInfo.getClientId());
                    }
                    clientSessionManager.unregisterClient(clientInfo.getClientId());
                }
                if (needChannelClose) {
                    sessionCtx.getChannel().close();
                }
                log.info("[{}][{}] Client disconnected.", sessionCtx.getClientId(), sessionId);
            } catch (Exception e) {
                log.warn("[{}][{}] Failed to disconnect client. Reason - {}.", sessionCtx.getClientId(), sessionId, e.getMessage());
                log.trace("Detailed error: ", e);
            } finally {
                sessionCtx.getLock().unlock();
            }
        });
    }

    private ClientInfo getClientInfo(SessionInfo sessionInfo) {
        if (sessionInfo == null || sessionInfo.getClientInfo() == null) {
            return null;
        }
        return sessionInfo.getClientInfo();
    }

    @PreDestroy
    public void destroy() {
        log.debug("Shutting down executor");
        disconnectExecutor.shutdown();
        try {
            if (!disconnectExecutor.awaitTermination(shutdownTimeout, TimeUnit.MILLISECONDS)) {
                log.warn("Failed to await termination of executor");
                disconnectExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Failed to await termination of executor");
            disconnectExecutor.shutdownNow();
        }
    }
}
