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
package org.thingsboard.mqtt.broker.service.limits;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;

@Component
@RequiredArgsConstructor
@Slf4j
@Conditional(RateLimitScheduledService.ClientSessionsLimitGreaterThanZeroCondition.class)
@ConditionalOnProperty(prefix = "mqtt", value = "sessions-limit-correction", havingValue = "true")
public class RateLimitScheduledService {

    private final ClientSessionService clientSessionService;
    private final RateLimitCacheService rateLimitCacheService;

    @PostConstruct
    public void init() {
        log.info("Initializing RateLimitScheduledService");
    }

    @Scheduled(fixedRateString = "${mqtt.sessions-limit-correction-period-ms}")
    public void scheduleSessionsLimitCorrection() {
        rateLimitCacheService.setSessionCount(clientSessionService.getClientSessionsCount());
    }

    public static class ClientSessionsLimitGreaterThanZeroCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getEnvironment().getProperty("mqtt.sessions-limit", Integer.class, 0) > 0;
        }
    }
}
