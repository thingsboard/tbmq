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
package org.thingsboard.mqtt.broker.common.util;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BandwidthBuilder.BandwidthBuilderBuildStage;
import io.github.bucket4j.BandwidthBuilder.BandwidthBuilderRefillStage;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.local.LocalBucket;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;

import java.time.Duration;

public class TbRateLimits {

    private final LocalBucket bucket;

    public TbRateLimits(String limitsConfiguration) {
        this(limitsConfiguration, false);
    }

    public TbRateLimits(String limitsConfiguration, boolean refillIntervally) {
        LocalBucketBuilder builder = Bucket.builder();
        boolean initialized = false;
        for (String limitSrc : limitsConfiguration.split(BrokerConstants.COMMA)) {
            String[] parts = limitSrc.split(BrokerConstants.COLON);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid limit format: " + limitSrc);
            }
            long capacity = Long.parseLong(parts[0]);
            long duration = Long.parseLong(parts[1]);
            BandwidthBuilderRefillStage refillStage = Bandwidth.builder().capacity(capacity);
            Duration refillDuration = Duration.ofSeconds(duration);
            BandwidthBuilderBuildStage stage = refillIntervally ?
                    refillStage.refillIntervally(capacity, refillDuration) : refillStage.refillGreedy(capacity, refillDuration);
            builder.addLimit(stage.build());
            initialized = true;
        }
        if (initialized) {
            bucket = builder.build();
        } else {
            throw new IllegalArgumentException("Failed to parse rate limits configuration: " + limitsConfiguration);
        }
    }

    public boolean tryConsume() {
        return bucket.tryConsume(1);
    }
}
