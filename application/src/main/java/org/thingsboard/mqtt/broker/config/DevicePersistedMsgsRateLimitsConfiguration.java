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
package org.thingsboard.mqtt.broker.config;

import io.github.bucket4j.BucketConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mqtt.rate-limits.device-persisted-messages")
@Data
@EqualsAndHashCode(callSuper = true)
public class DevicePersistedMsgsRateLimitsConfiguration extends AbstractMsgsRateLimitsConfiguration {

    @Bean
    @Conditional(OnDevicePersistedMsgsRateLimitsEnabledCondition.class)
    public BucketConfiguration devicePersistedMsgsBucketConfiguration() {
        return getBucketConfiguration();
    }

    private static class OnDevicePersistedMsgsRateLimitsEnabledCondition extends OnEnabledCondition {
        @Override
        protected String getEnabledProperty() {
            return "mqtt.rate-limits.device-persisted-messages.enabled";
        }
    }
}
