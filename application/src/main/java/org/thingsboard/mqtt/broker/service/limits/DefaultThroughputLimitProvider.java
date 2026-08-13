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

import io.github.bucket4j.Bandwidth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DefaultThroughputLimitProvider implements ThroughputLimitProvider {

    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration;

    @Override
    public long getSustainedRatePerSec() {
        long minRatePerSec = Long.MAX_VALUE;
        // read the rate off the parsed bandwidths rather than re-splitting the config string, so this cannot
        // disagree with the parser that builds the bucket about what is valid
        for (Bandwidth bandwidth : totalMsgsRateLimitsConfiguration.parseBandwidths()) {
            // the config format is capacity:SECONDS, so the division is exact; the floor at 1 keeps a slow
            // bandwidth from deriving a zero block size
            long periodSeconds = Math.max(1, bandwidth.getRefillPeriodNanos() / NANOS_PER_SECOND);
            minRatePerSec = Math.min(minRatePerSec, Math.max(1, bandwidth.getRefillTokens() / periodSeconds));
        }
        return minRatePerSec;
    }
}
