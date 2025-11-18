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
package org.thingsboard.mqtt.broker.service.analysis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "analysis.log")
public class AnalysisLogConfiguration {

    /**
     * Global switch for this logging (in addition to log level).
     */
    private boolean enabled = false;

    /**
     * If true – logs for all clients, ignoring analyzedClientIds.
     */
    private boolean allClients = false;

    /**
     * If allClients == false, only these clientIds will produce logs.
     */
    private Set<String> analyzedClientIds = Collections.emptySet();

    boolean isDisabled() {
        return !enabled;
    }
}
