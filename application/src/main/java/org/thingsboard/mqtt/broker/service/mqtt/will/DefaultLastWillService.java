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
package org.thingsboard.mqtt.broker.service.mqtt.will;

import io.netty.handler.codec.mqtt.MqttProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultLastWillService implements LastWillService {

    private final ConcurrentMap<UUID, MsgWithSessionInfo> lastWillMessages = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScheduledFuture<?>> delayedLastWillFuturesMap = new ConcurrentHashMap<>();

    private final MsgDispatcherService msgDispatcherService;
    private final RetainedMsgProcessor retainedMsgProcessor;
    private final StatsManager statsManager;

    @Setter
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        statsManager.registerLastWillStats(lastWillMessages);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("last-will-scheduler"));
    }

    @PreDestroy
    public void destroy() {
        if (this.scheduler != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(scheduler, "Last will scheduler");
        }
    }

    @Override
    public void saveLastWillMsg(SessionInfo sessionInfo, PublishMsg publishMsg) {
        log.trace("[{}][{}] Saving last will msg, topic - [{}]",
                sessionInfo.getClientId(), sessionInfo.getSessionId(), publishMsg.getTopicName());
        lastWillMessages.compute(sessionInfo.getSessionId(), (sessionId, lastWillMsg) -> {
            if (lastWillMsg != null) {
                log.error("[{}][{}] Last-will message has been saved already!", sessionInfo.getClientId(), sessionId);
            }
            return new MsgWithSessionInfo(publishMsg, sessionInfo);
        });
    }

    @Override
    public void removeAndExecuteLastWillIfNeeded(MqttDisconnectMsg disconnectMsg, int sessionExpiryInterval) {
        UUID sessionId = disconnectMsg.getSessionId();
        boolean sendMsg = disconnectMsg.getReason().getType().allowsLastWillOnDisconnect();
        var newSessionCleanStart = disconnectMsg.isNewSessionCleanStart();

        MsgWithSessionInfo lastWillMsgWithSessionInfo = lastWillMessages.get(sessionId);
        if (lastWillMsgWithSessionInfo == null) {
            log.trace("[{}] No last will msg found", sessionId);
            return;
        }

        log.debug("[{}][{}] Removing last will msg, sendMsg - {}", lastWillMsgWithSessionInfo.getClientId(), sessionId, sendMsg);
        lastWillMessages.remove(sessionId);
        if (sendMsg) {
            try {
                int willDelay = getWillDelay(lastWillMsgWithSessionInfo, sessionExpiryInterval);
                if (!newSessionCleanStart && willDelay > 0) {
                    return;
                }
                scheduleLastWill(lastWillMsgWithSessionInfo, willDelay);
            } catch (Exception e) {
                log.warn("[{}] Failed to send last will msg", lastWillMsgWithSessionInfo.getClientId(), e);
            }
        }
    }

    @Override
    public void cancelLastWillDelayIfScheduled(String clientId) {
        ScheduledFuture<?> task = delayedLastWillFuturesMap.get(clientId);
        if (task != null && !task.isCancelled()) {
            task.cancel(true);
        }
    }

    void scheduleLastWill(MsgWithSessionInfo lastWillMsgWithSessionInfo, int willDelay) {
        if (scheduler == null || scheduler.isShutdown()) return;
        log.debug("[{}][{}] Schedule last will with delay {}", lastWillMsgWithSessionInfo.getClientId(), lastWillMsgWithSessionInfo.getTopicName(), willDelay);
        ScheduledFuture<?> futureTask = scheduler.schedule(() -> processLastWill(lastWillMsgWithSessionInfo), willDelay, TimeUnit.SECONDS);
        delayedLastWillFuturesMap.put(getClientId(lastWillMsgWithSessionInfo), futureTask);
    }

    private int getWillDelay(MsgWithSessionInfo lastWillMsgWithSessionInfo, int sessionExpiryIntervalFromDisconnect) {
        SessionInfo sessionInfo = lastWillMsgWithSessionInfo.getSessionInfo();
        int sessionExpiryInterval = getSessionExpiryInterval(sessionExpiryIntervalFromDisconnect, sessionInfo);

        MqttProperties.IntegerProperty willDelayProperty = MqttPropertiesUtil.getWillDelayProperty(lastWillMsgWithSessionInfo.getProperties());
        if (willDelayProperty != null) {
            if (!sessionInfo.isCleanStart() && sessionExpiryInterval == 0) {
                return willDelayProperty.value();
            }
            return Math.min(willDelayProperty.value(), sessionExpiryInterval);
        }
        return 0;
    }

    private int getSessionExpiryInterval(int sessionExpiryIntervalFromDisconnect, SessionInfo sessionInfo) {
        return sessionExpiryIntervalFromDisconnect == -1 ? sessionInfo.safeGetSessionExpiryInterval() : sessionExpiryIntervalFromDisconnect;
    }

    private void processLastWill(MsgWithSessionInfo lastWillMsgWithSessionInfo) {
        PublishMsg publishMsg = lastWillMsgWithSessionInfo.getPublishMsg();
        if (publishMsg.isRetained()) {
            publishMsg = retainedMsgProcessor.process(publishMsg);
        }
        persistPublishMsg(lastWillMsgWithSessionInfo.getSessionInfo(), publishMsg);
        delayedLastWillFuturesMap.remove(getClientId(lastWillMsgWithSessionInfo));
    }

    private String getClientId(MsgWithSessionInfo lastWillMsgWithSessionInfo) {
        return lastWillMsgWithSessionInfo.getClientId();
    }

    void persistPublishMsg(SessionInfo sessionInfo, PublishMsg publishMsg) {
        msgDispatcherService.persistPublishMsg(sessionInfo, publishMsg,
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        log.trace("[{}][{}] Successfully acknowledged last will msg.", sessionInfo.getClientId(), sessionInfo.getSessionId());
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.warn("[{}][{}] Failed to acknowledge last will msg.", sessionInfo.getClientId(), sessionInfo.getSessionId(), t);
                    }
                });
    }

    @AllArgsConstructor
    @Data
    public static class MsgWithSessionInfo {

        private final PublishMsg publishMsg;
        private final SessionInfo sessionInfo;

        private String getClientId() {
            return sessionInfo.getClientId();
        }

        private String getTopicName() {
            return publishMsg.getTopicName();
        }

        private MqttProperties getProperties() {
            return publishMsg.getProperties();
        }
    }
}
