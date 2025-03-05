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
package org.thingsboard.mqtt.broker.integration.service.limit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.limit.LimitedApi;
import org.thingsboard.mqtt.broker.common.util.TbRateLimits;

@Service
@Slf4j
public class DefaultRateLimitService implements RateLimitService {

    private TbRateLimits integrationsRateLimit;

    @Override
    public void initIntegrationRateLimit(String integrationEventsRateLimitsConf) {
        integrationsRateLimit = new TbRateLimits(integrationEventsRateLimitsConf, LimitedApi.INTEGRATION_EVENTS.isRefillRateLimitIntervally());
    }

    @Override
    public boolean checkRateLimit(LimitedApi api) {
        log.trace("Checking rate limit for {}", api);

        if (integrationsRateLimit == null) {
            return true;
        }

        boolean success = integrationsRateLimit.tryConsume();
        if (!success) {
            log.debug("Rate limit exceeded for {}", api);
        }
        return success;
    }

}
