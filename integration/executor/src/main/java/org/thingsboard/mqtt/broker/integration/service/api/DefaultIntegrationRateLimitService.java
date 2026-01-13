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
package org.thingsboard.mqtt.broker.integration.service.api;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.limit.LimitedApi;
import org.thingsboard.mqtt.broker.common.util.DeduplicationUtil;
import org.thingsboard.mqtt.broker.exception.TbRateLimitsException;
import org.thingsboard.mqtt.broker.integration.service.limit.RateLimitService;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultIntegrationRateLimitService implements IntegrationRateLimitService {

    @Value("${event.error.rate-limits.enabled}")
    private boolean eventRateLimitsEnabled;

    @Value("${event.error.rate-limits.integration}")
    private String integrationEventsRateLimitsConf;

    @Value("#{${event.error.rate-limits.ttl-minutes} * 60 * 1000}")
    private long deduplicationDurationMs;

    private final @Lazy RateLimitService rateLimitService;

    @PostConstruct
    public void init() {
        if (eventRateLimitsEnabled) {
            rateLimitService.initIntegrationRateLimit(integrationEventsRateLimitsConf);
        }
    }

    @Override
    public boolean checkLimit(UUID integrationId, boolean throwException) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing integration error event msg.", integrationId);
        }
        if (!eventRateLimitsEnabled) {
            return true;
        }

        if (!rateLimitService.checkRateLimit(LimitedApi.INTEGRATION_EVENTS)) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Integration level error rate limit detected.", integrationId);
            }
            if (throwException) {
                throw new TbRateLimitsException("Integration error rate limits reached!");
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean alreadyProcessed(UUID entityId) {
        return DeduplicationUtil.alreadyProcessed(DeduplicationKey.of(entityId), deduplicationDurationMs);
    }

    @Data(staticConstructor = "of")
    private static class DeduplicationKey {
        private final UUID entityId;
    }

}
