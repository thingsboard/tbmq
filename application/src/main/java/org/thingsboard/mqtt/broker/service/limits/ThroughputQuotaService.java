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
package org.thingsboard.mqtt.broker.service.limits;

/**
 * Cluster-wide total throughput quota — the single policy owner for the billed unit: MQTT PUBLISH
 * packets processed, incoming and outgoing combined (spec: docs/superpowers/specs/
 * 2026-08-06-total-throughput-quota-design.md). Charges are node-local atomic operations; the
 * shared Redis bucket is only touched by asynchronous block draws, never by callers.
 */
public interface ThroughputQuotaService {

    /**
     * Charges one incoming PUBLISH packet.
     *
     * @return true if granted; false if the packet must be refused (caller performs the terminal
     * refusal action, e.g. PUBACK QUOTA_EXCEEDED)
     */
    boolean tryConsumeIncoming();

    /**
     * Charges {@code n} outgoing PUBLISH packets.
     *
     * @return the granted count in {@code [0..n]}; callers deliver the granted prefix and
     * terminally settle the remainder
     */
    int tryConsumeOutgoing(int n);
}
