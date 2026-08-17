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
 * Cluster-wide quota over MQTT PUBLISH packets, incoming and outgoing combined; a copy routed to an
 * integration counts as outgoing.
 * <p>
 * Packets are charged at ADMISSION, before they are stored: a publish costs 1 for the incoming message plus
 * 1 per persistent subscriber, shared subscription group and integration it fans out to. Delivering a stored
 * message is never charged again, so backlog replay and QoS 1/2 retransmissions are free.
 * <p>
 * Charges are node-local atomic operations refilled by asynchronous block draws against the shared Redis
 * bucket, so no charge touches Redis on the calling thread except {@link #tryConsumeOutgoingBlocking(int)}.
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
     * Charges {@code n} outgoing PUBLISH packets without touching Redis, so the grant is capped by the node-local
     * pool. Kept as the non-blocking bulk charge for callers that must not block; currently used only by tests.
     *
     * @return the granted count in {@code [0..n]}; the caller delivers that prefix and settles the remainder
     */
    int tryConsumeOutgoing(int n);

    /**
     * Charges {@code n} outgoing PUBLISH packets, drawing from the shared bucket ON THE CALLING THREAD when the
     * node-local pool falls short, so a wide fan-out is bounded by the cluster budget rather than by this node's
     * lease. Only for background paths where a Redis round trip is acceptable - the persistent fan-out and retained
     * delivery on SUBSCRIBE. Never call it from the Netty ingress path: it would stall every socket on that event
     * loop.
     *
     * @return the granted count in {@code [0..n]}; a remainder means the shared bucket is dry, so the caller
     * settles it terminally
     */
    int tryConsumeOutgoingBlocking(int n);
}
