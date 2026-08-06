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
 * Tracks silent degradations of the cluster-wide total throughput quota as a cumulative
 * {@code throughputQuotaDegraded} Prometheus counter tagged {@code cause=redis}. The quota fails
 * OPEN on Redis errors — traffic passes unmetered and nothing lands in {@code droppedMsgs} — so
 * this counter is the only signal that enforcement has quietly stopped.
 * <p>
 * Obtained from {@link StatsManager}, so it shares the {@code stats.enabled} master switch; when
 * stats are disabled the stub implementation is a no-op and nothing is exposed.
 */
public interface ThroughputQuotaStats {

    void incrementRedisDegraded();

    int getCount();

    /**
     * Clears the per-interval count read by {@link #getCount()}. The cumulative Prometheus counter
     * is unaffected and stays monotonic.
     */
    void reset();
}
