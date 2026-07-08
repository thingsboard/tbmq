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

import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

/**
 * Tracks client disconnects as a cumulative {@code clientDisconnects} Prometheus counter. In CE the counter is
 * untagged, so {@code rate(clientDisconnects_total[...])} yields the broker-wide disconnect rate; the per-reason
 * breakdown is a PE-only extension that overrides {@link #increment(DisconnectReasonType)}.
 * <p>
 * Obtained from {@link StatsManager}, so it shares the {@code stats.enabled} master switch with every other
 * broker metric; when stats are disabled the stub implementation is a no-op and nothing is exposed.
 */
public interface ClientDisconnectStats {

    /**
     * Records one client disconnect. {@code reasonType} is carried so the PE extension can tag/persist by
     * reason; the CE implementation ignores it and increments a single untagged counter.
     */
    void increment(DisconnectReasonType reasonType);

    /**
     * Number of disconnects since the last {@link #reset()} — read for the periodic stats log line.
     */
    int getCount();

    /**
     * Clears the per-interval count read by {@link #getCount()}. The cumulative {@code clientDisconnect}
     * Prometheus counter is unaffected and stays monotonic.
     */
    void reset();
}
