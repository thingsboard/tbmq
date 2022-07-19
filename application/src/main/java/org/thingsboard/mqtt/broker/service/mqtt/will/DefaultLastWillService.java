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
package org.thingsboard.mqtt.broker.service.mqtt.will;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import javax.annotation.PostConstruct;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultLastWillService implements LastWillService {
    private final ConcurrentMap<UUID, MsgWithSessionInfo> lastWillMessages = new ConcurrentHashMap<>();

    private final MsgDispatcherService msgDispatcherService;
    private final StatsManager statsManager;

    @PostConstruct
    public void init() {
        statsManager.registerLastWillStats(lastWillMessages);
    }

    @Override
    public void saveLastWillMsg(SessionInfo sessionInfo, PublishMsg publishMsg) {
        if (log.isTraceEnabled())
            log.trace("[{}][{}] Saving last will msg, topic - [{}]",
                    sessionInfo.getClientInfo().getClientId(), sessionInfo.getSessionId(), publishMsg.getTopicName());

        lastWillMessages.compute(sessionInfo.getSessionId(), (sessionId, lastWillMsg) -> {
            if (lastWillMsg != null) {
                log.error("[{}][{}] Last-will message has been saved already!", sessionInfo.getClientInfo().getClientId(), sessionId);
            }
            return new MsgWithSessionInfo(publishMsg, sessionInfo);
        });
    }

    @Override
    public void removeAndExecuteLastWillIfNeeded(UUID sessionId, boolean sendMsg) {
        MsgWithSessionInfo lastWillMsg = lastWillMessages.get(sessionId);
        if (lastWillMsg == null) {
            log.trace("[{}] No last will msg.", sessionId);
            return;
        }

        log.debug("[{}] Removing last will msg, sendMsg - {}", sessionId, sendMsg);
        lastWillMessages.remove(sessionId);
        if (sendMsg) {
            persistPublishMsg(lastWillMsg, sessionId);
        }
    }

    void persistPublishMsg(MsgWithSessionInfo lastWillMsg, UUID sessionId) {
        msgDispatcherService.persistPublishMsg(lastWillMsg.sessionInfo, lastWillMsg.publishMsg,
                new TbQueueCallback() {
                    @Override
                    public void onSuccess(TbQueueMsgMetadata metadata) {
                        log.trace("[{}] Successfully acknowledged last will msg.", sessionId);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.warn("[{}] Failed to acknowledge last will msg. Reason - {}.", sessionId, t.getMessage());
                        log.trace("Detailed error:", t);
                    }
                });
    }

    @AllArgsConstructor
    public static class MsgWithSessionInfo {
        private final PublishMsg publishMsg;
        private final SessionInfo sessionInfo;
    }
}
