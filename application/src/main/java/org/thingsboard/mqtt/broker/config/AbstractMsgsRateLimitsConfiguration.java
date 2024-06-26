/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConfigurationBuilder;
import lombok.Data;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.thingsboard.mqtt.broker.cache.CacheConstants;

import java.time.Duration;

@Data
public abstract class AbstractMsgsRateLimitsConfiguration {

    protected boolean enabled;
    protected String config;

    protected BucketConfiguration getBucketConfiguration() {
        ConfigurationBuilder builder = BucketConfiguration.builder();
        for (String limitSrc : config.split(CacheConstants.COMMA)) {
            String[] parts = limitSrc.split(CacheConstants.COLON);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid limitSrc format: " + limitSrc);
            }

            long capacity = Long.parseLong(parts[0]);
            long durationInSeconds = Long.parseLong(parts[1]);

            builder.addLimit(Bandwidth.builder()
                    .capacity(capacity)
                    .refillGreedy(capacity, Duration.ofSeconds(durationInSeconds))
                    .build());
        }
        return builder.build();
    }

    protected abstract static class OnEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getEnvironment().getProperty(getEnabledProperty(), Boolean.class, false);
        }

        protected abstract String getEnabledProperty();

    }
}
