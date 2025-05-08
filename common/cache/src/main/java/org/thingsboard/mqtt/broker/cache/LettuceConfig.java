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
package org.thingsboard.mqtt.broker.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lettuce.config")
public class LettuceConfig {

    private int commandTimeout;
    private int shutdownQuietPeriod;
    private int shutdownTimeout;
    private ClusterConfig cluster;

    @Data
    public static class ClusterConfig {
        private LettuceTopologyRefreshConfig topologyRefresh;

        @Data
        public static class LettuceTopologyRefreshConfig {

            private boolean enabled;
            private int period;

        }

    }

}
