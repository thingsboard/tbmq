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
 * packets processed, incoming and outgoing combined; messages routed to integration subscribers
 * count as outgoing (spec: docs/superpowers/specs/2026-08-12-prepaid-quota-charging-design.md).
 * Packets are charged at ADMISSION, before they are stored: a publish costs 1 for the incoming message
 * plus 1 per persistent subscriber, shared subscription group and integration it fans out to, all charged
 * on the publish path. Delivery of a stored message is never charged again, so backlog replay and QoS 1/2
 * retransmissions are free.
 * Charges are node-local atomic operations backed by asynchronous block draws against the shared Redis
 * bucket. The one exception is {@link #tryConsumeOutgoingWaiting(int)}, which draws on the calling thread
 * so that a transient node-local shortfall cannot truncate a fan-out the cluster still has budget for.
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
     * Charges one outgoing PUBLISH packet.
     *
     * @return true if granted; false if the packet must be dropped (caller performs the terminal
     * drop action, including any droppedMsgs reporting)
     */
    boolean tryConsumeOutgoing();

    /**
     * Charges {@code n} outgoing PUBLISH packets without ever touching Redis, so the grant is capped at the
     * node-local pool plus one block of bounded credit.
     * <p>
     * RETAINED DELIBERATELY, despite having no production caller since the fan-out charges moved to
     * {@link #tryConsumeOutgoingWaiting(int)}: it is the only non-blocking bulk charge in this API — the method
     * to reach for if the retained-delivery path should ever stop blocking the subscription-persist I/O thread —
     * and {@code ThroughputQuotaServiceImplTest} uses it to drain the local pool while asserting something else.
     * Not dead code to be swept.
     *
     * @return the granted count in {@code [0..n]}; callers deliver the granted prefix and
     * terminally settle the remainder
     */
    int tryConsumeOutgoing(int n);

    /**
     * Charges {@code n} outgoing PUBLISH packets, drawing from the shared bucket ON THE CALLING THREAD when
     * the node-local pool falls short, so a wide fan-out is bounded by the cluster budget rather than by the
     * node's lease. Never call this from the Netty ingress path, where a Redis round trip would stall every
     * other socket on that event loop. The two permitted callers:
     * <ul>
     *     <li>the persistent fan-out in {@code MsgPersistenceManagerImpl}, on the publish-dispatcher Kafka
     *     consumer thread, where blocking is normal and delays only that partition's dispatch;</li>
     *     <li>retained delivery on SUBSCRIBE, which runs on the {@code client-subscriptions} Kafka producer's
     *     single I/O thread (the persist callback). A draw there also delays other clients' SUBACKs, which is
     *     accepted because it is ONE bulk draw per retained-matching SUBSCRIBE rather than one per message,
     *     and a Redis failure opens the fail-open window that makes this method return immediately.</li>
     * </ul>
     *
     * @return the granted count in {@code [0..n]}; a remainder means the shared bucket is genuinely dry, so
     * callers settle it terminally
     */
    int tryConsumeOutgoingWaiting(int n);
}
