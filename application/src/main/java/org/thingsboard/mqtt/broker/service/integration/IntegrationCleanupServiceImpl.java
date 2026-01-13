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
package org.thingsboard.mqtt.broker.service.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.util.CallbackUtil;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class IntegrationCleanupServiceImpl {

    private final IntegrationService integrationService;
    private final IntegrationTopicService integrationTopicService;

    @Value("#{${integrations.cleanup.ttl:604800} * 1000}")
    private long ttlMs;

    @Scheduled(fixedRateString = "${integrations.cleanup.period}", timeUnit = TimeUnit.SECONDS)
    public void cleanUp() {
        if (ttlMs <= 0) {
            log.debug("Integrations cleanup is disabled: {}ms", ttlMs);
            return;
        }
        log.info("Starting cleaning up expired disconnected integrations");

        long currentTs = System.currentTimeMillis();
        //todo: improve by fetching in batches
        int count = 0;
        try {
            for (Integration integration : integrationService.findAllIntegrations()) {
                if (needsToBeRemoved(currentTs, integration)) {
                    count++;
                    log.debug("[{}][{}] Cleaning up expired disconnected integration", integration.getId(), integration.getName());
                    deleteIntegrationTopic(integration.getIdStr());
                }
            }
        } catch (Throwable t) {
            log.warn("Failed to clean up expired disconnected integrations", t);
        }
        log.info("Cleaning up of [{}] expired disconnected integrations is finished", count);
    }

    public void deleteIntegrationTopic(String integrationId) {
        integrationTopicService.deleteTopic(integrationId, CallbackUtil.EMPTY);
    }

    private boolean needsToBeRemoved(long currentTs, Integration integration) {
        return !integration.isEnabled() && isExpired(integration, currentTs);
    }

    private boolean isExpired(Integration integration, long currentTs) {
        return integration.getDisconnectedTime() + ttlMs < currentTs;
    }
}
