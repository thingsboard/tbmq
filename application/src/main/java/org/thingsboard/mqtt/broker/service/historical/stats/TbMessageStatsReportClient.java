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
package org.thingsboard.mqtt.broker.service.historical.stats;

import org.thingsboard.mqtt.broker.common.data.BrokerConstants;

/**
 * Collects the historical (System B) usage timeseries shown on the TBMQ UI monitoring charts.
 * <p>
 * These historical values are <b>per-interval deltas</b>: each reporting cron accumulates counts and
 * persists them with {@code getAndSet(0)}. This is intentionally a different shape from the Micrometer
 * meters exposed on {@code /actuator/prometheus} (via {@code StatsManager}), which are
 * <b>cumulative/monotonic lifetime totals</b>. So the same underlying events produce very different raw
 * numbers on the two systems — a per-minute delta on the chart vs. a lifetime total in Prometheus.
 * <p>
 * The two systems can also diverge in <b>gating</b>: historical reporting is gated on
 * {@code historical-data-report.enabled}, whereas several Micrometer counters (notably {@code droppedMsgs},
 * see {@link #reportDroppedMsgs()}) increment unconditionally — so with historical reporting disabled
 * Prometheus still counts while the chart shows nothing.
 * <p>
 * <b>Known limitation (async-save loss):</b> interval deltas are saved asynchronously. If a save/Kafka-send
 * fails, that interval's delta is lost permanently from the chart, while the cumulative Micrometer meter is
 * unaffected — so under save failures the chart can undercount relative to Prometheus.
 */
public interface TbMessageStatsReportClient {

    /**
     * Reports a generic usage stat under the given key.
     * <p>
     * Do not use this for {@link BrokerConstants#DROPPED_MSGS}: dropped messages must be reported via
     * {@link #reportDroppedMsgs()} / {@link #reportDroppedMsgs(int)} so the {@code droppedMsgs} Prometheus
     * counter exposed on {@code /actuator/prometheus} stays in sync.
     */
    void reportStats(String key);

    /**
     * Reports a generic usage stat under the given key, incremented by {@code count}.
     * <p>
     * Do not use this for {@link BrokerConstants#DROPPED_MSGS}: dropped messages must be reported via
     * {@link #reportDroppedMsgs()} / {@link #reportDroppedMsgs(int)} so the {@code droppedMsgs} Prometheus
     * counter exposed on {@code /actuator/prometheus} stays in sync.
     */
    void reportStats(String key, int count);

    /**
     * Reports a single dropped message. Increments the {@code droppedMsgs} Prometheus counter (always,
     * regardless of {@code historical-data-report.enabled}) and the historical stat (when enabled).
     */
    void reportDroppedMsgs();

    /**
     * Reports {@code count} dropped messages. Increments the {@code droppedMsgs} Prometheus counter (always,
     * regardless of {@code historical-data-report.enabled}) and the historical stat (when enabled).
     */
    void reportDroppedMsgs(int count);

    void reportInboundTraffic(long bytes);

    void reportOutBoundTraffic(long bytes);

    void reportClientSendStats(String clientId, int qos);

    void reportClientReceiveStats(String clientId, int qos);

    void removeClient(String clientId);
}
