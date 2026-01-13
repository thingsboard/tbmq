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
package org.thingsboard.mqtt.broker.service.system;

public enum ServiceStatus {

    ACTIVE,      // updated within the last hour
    INACTIVE,    // updated >1 hour but ≤1 week ago
    OUTDATED;     // updated over a week ago

    private static final long ONE_HOUR_MS = 60 * 60 * 1000;
    private static final long ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000;

    public static ServiceStatus fromLastUpdateTime(Long lastUpdateTime) {
        if (lastUpdateTime == null || lastUpdateTime == 0) return OUTDATED;

        long now = System.currentTimeMillis();
        long age = now - lastUpdateTime;

        if (age <= ONE_HOUR_MS) {
            return ACTIVE;
        } else if (age <= ONE_WEEK_MS) {
            return INACTIVE;
        } else {
            return OUTDATED;
        }
    }
}
