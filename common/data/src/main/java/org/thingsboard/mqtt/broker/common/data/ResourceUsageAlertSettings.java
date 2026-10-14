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
package org.thingsboard.mqtt.broker.common.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Rows saved by earlier builds also carry the dropped email fields.
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUsageAlertSettings {

    public static final int DEFAULT_HIGH_USAGE = 75;
    public static final int DEFAULT_CRITICAL_USAGE = 85;
    public static final int MIN_USAGE = 1;
    public static final int MAX_USAGE = 100;

    private int highUsage = DEFAULT_HIGH_USAGE;
    private int criticalUsage = DEFAULT_CRITICAL_USAGE;

    public static ResourceUsageAlertSettings defaults() {
        return new ResourceUsageAlertSettings();
    }

    public boolean hasValidThresholds() {
        return highUsage >= MIN_USAGE && highUsage < criticalUsage && criticalUsage <= MAX_USAGE;
    }

}
