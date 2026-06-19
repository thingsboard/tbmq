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
