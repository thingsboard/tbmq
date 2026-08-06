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
package org.thingsboard.mqtt.broker.service.limits;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;

@Service
@RequiredArgsConstructor
public class DefaultThroughputLimitProvider implements ThroughputLimitProvider {

    private final TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration;

    @Override
    public long getSustainedRatePerSec() {
        long minRatePerSec = Long.MAX_VALUE;
        for (String limitSrc : totalMsgsRateLimitsConfiguration.getConfig().split(BrokerConstants.COMMA)) {
            String[] parts = limitSrc.split(BrokerConstants.COLON);
            long capacity = Long.parseLong(parts[0]);
            long durationInSeconds = Long.parseLong(parts[1]);
            minRatePerSec = Math.min(minRatePerSec, Math.max(1, capacity / durationInSeconds));
        }
        return minRatePerSec;
    }
}
