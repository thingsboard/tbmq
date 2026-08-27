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
package org.thingsboard.mqtt.broker.service.stats;

/**
 * Counts failed draws against the shared Redis bucket as {@code throughputQuotaDegraded{cause=redis}}.
 * Within {@code mqtt.rate-limits.total.degraded-grace-ms} the quota fails OPEN on Redis errors - traffic passes
 * unmetered and nothing lands in {@code droppedMsgs} - so inside that window this counter is the only signal that
 * enforcement has stopped. Past the grace the node refuses PUBLISH packets instead, and those refusals do land in
 * {@code droppedMsgs}. Draws run only when traffic charges the quota, so the counter stays flat on an idle node:
 * alert on Redis reachability and treat this counter as corroboration, not as the outage alarm.
 * <p>
 * Obtained from {@link StatsManager}, so it shares the {@code stats.enabled} switch.
 */
public interface ThroughputQuotaStats {

    void incrementRedisDegraded();

    int getCount();

    /**
     * Clears the per-interval count read by {@link #getCount()}. The Prometheus counter stays monotonic.
     */
    void reset();
}
