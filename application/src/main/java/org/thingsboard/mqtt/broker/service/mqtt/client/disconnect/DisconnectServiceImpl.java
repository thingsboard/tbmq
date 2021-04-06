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
package org.thingsboard.mqtt.broker.service.mqtt.client.disconnect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.SessionState;

import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisconnectServiceImpl implements DisconnectService {
    private final ExecutorService disconnectExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("disconnect-executor"));

    private final KeepAliveService keepAliveService;
    private final LastWillService lastWillService;
    private final SubscriptionManager subscriptionManager;
    private final ClientSessionCtxService clientSessionCtxService;
    private final MsgPersistenceManager msgPersistenceManager;
    private final ClientSessionEventService clientSessionEventService;

    @Value("${application.disconnect.executor-shutdown-timout-ms:500}")
    private long shutdownTimeout;

    @Override
    public void disconnect(ClientSessionCtx sessionCtx, DisconnectReason reason) {
        disconnect(sessionCtx, reason, true);
    }

    @Override
    public void disconnect(ClientSessionCtx sessionCtx, DisconnectReason reason, boolean needChannelClose) {
        SessionState prevSessionState = sessionCtx.updateSessionState(SessionState.DISCONNECTED);
        if (prevSessionState == SessionState.DISCONNECTED) {
            return;
        }
        UUID sessionId = sessionCtx.getSessionId();
        log.trace("[{}][{}] Init client disconnection. Reason - {}.", sessionCtx.getClientId(), sessionId, reason);
        disconnectExecutor.execute(() -> {
            sessionCtx.getProcessingLock().lock();
            try {
                clearClientSession(sessionCtx, reason);
            } catch (Exception e) {
                log.warn("[{}][{}] Failed to clean client session. Reason - {}.", sessionCtx.getClientId(), sessionId, e.getMessage());
                log.trace("Detailed error: ", e);
            } finally {
                sessionCtx.getProcessingLock().unlock();
            }
            if (needChannelClose) {
                try {
                    sessionCtx.getChannel().close();
                } catch (Exception e) {
                    log.debug("[{}][{}] Failed to close channel. Reason - {}.", sessionCtx.getClientId(), sessionId, e.getMessage());
                }
            }
            log.info("[{}][{}] Client disconnected.", sessionCtx.getClientId(), sessionId);
        });
    }

    private void clearClientSession(ClientSessionCtx sessionCtx, DisconnectReason disconnectReason) {
        UUID sessionId = sessionCtx.getSessionId();

        sessionCtx.getUnprocessedMessagesQueue().release();

        keepAliveService.unregisterSession(sessionId);

        ClientInfo clientInfo = sessionCtx.getSessionInfo() != null ? sessionCtx.getSessionInfo().getClientInfo() : null;
        if (clientInfo == null) {
            return;
        }

        boolean sendLastWill = !DisconnectReason.ON_DISCONNECT_MSG.equals(disconnectReason);
        lastWillService.removeLastWill(sessionId, sendLastWill);

        if (!sessionCtx.getSessionInfo().isPersistent()) {
            subscriptionManager.clearSubscriptions(clientInfo.getClientId());
        } else {
            msgPersistenceManager.stopProcessingPersistedMessages(clientInfo);
        }
        clientSessionCtxService.unregisterSession(clientInfo.getClientId());
        clientSessionEventService.disconnect(clientInfo, sessionId);
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
