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
package org.thingsboard.mqtt.broker.service.mqtt.client.connect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.client.disconnect.DisconnectService;
import org.thingsboard.mqtt.broker.service.mqtt.handlers.MqttMessageHandler;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostConnectServiceImpl implements PostConnectService {
    private final ExecutorService queuedMessagesExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("unprocessed-messages-executor"));

    private final MsgPersistenceManager msgPersistenceManager;
    private final MqttMessageHandler mqttMessageHandler;
    private final DisconnectService disconnectService;

    @Override
    public void process(ClientSessionCtx ctx) throws MqttException {
        if (ctx.getSessionInfo().isPersistent()) {
            msgPersistenceManager.processPersistedMessages(ctx);
        }

        processQueuedMessages(ctx);
    }

    private void processQueuedMessages(ClientSessionCtx ctx) {
        queuedMessagesExecutor.execute(() -> {
            boolean wasConnectionLocked = false;
            try {
                ctx.getUnprocessedMessagesQueue().process(msg -> mqttMessageHandler.process(ctx, msg));
                ctx.getConnectionLock().lock();
                wasConnectionLocked = true;
                ctx.getUnprocessedMessagesQueue().process(msg -> mqttMessageHandler.process(ctx, msg));
                ctx.getUnprocessedMessagesQueue().finishProcessing();
            } catch (Exception e) {
                log.warn("[{}][{}] Failed to process msg. Reason - {}.",
                        ctx.getClientId(), ctx.getSessionId(), e.getMessage());
                log.trace("Detailed error: ", e);
                disconnectService.disconnect(ctx, DisconnectReason.ON_ERROR);
            } finally {
                if (wasConnectionLocked) {
                    ctx.getConnectionLock().unlock();
                }
            }
        });
    }


    @PreDestroy
    public void destroy() {
        log.debug("Shutting down executors");
        queuedMessagesExecutor.shutdownNow();
    }
}
