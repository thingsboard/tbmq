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
package org.thingsboard.mqtt.broker.dao.messages;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The per-queue message store behind the DEVICE persistence in the standalone edition. Every method is synchronous
 * and throws {@link org.thingsboard.mqtt.broker.dao.store.StoreException} when the store fails.
 */
public interface DeviceMsgQueueStore {

    /**
     * Appends to one or more queues in a single atomic write batch.
     */
    List<AppendResult> append(List<QueueAppend> appends);

    /**
     * @return up to {@code limit} live entries after {@code afterSequence}, in order; an expired entry met on the way is
     * deleted and not returned
     */
    List<StoredDeviceMsg> read(String queueKey, long afterSequence, int limit);

    /**
     * Reads like {@link #read} and, in the same atomic step, raises the sent mark of the queue to the last entry it
     * returns, so that neither a sweep nor a trim deletes a returned entry as expired ([MQTT-4.3.3-7],
     * [MQTT-4.4.0-1]). The {@code sent} flag of each returned entry is the one it had before this call. The sent mark
     * never goes down.
     */
    List<StoredDeviceMsg> readForDelivery(String queueKey, long afterSequence, int limit);

    /**
     * @return {@code false} when the queue has no such entry
     */
    boolean remove(String queueKey, int packetId);

    /**
     * @return {@code false} when the queue has no such entry
     */
    boolean markPubRel(String queueKey, int packetId);

    void removeQueue(String queueKey);

    /**
     * @return the number of entries moved; it includes the entries the limit of the target queue then trims
     */
    int moveQueue(String fromQueueKey, String toQueueKey, UnaryOperator<byte[]> bodyMapper);

    /**
     * Sets the last packet id of a queue; applies only when the queue has no entries.
     *
     * @return {@code true} when the packet id was set
     */
    boolean resetPacketId(String queueKey, int lastPacketId);

    DeviceQueueInfo info(String queueKey);

    /**
     * @return the number of entries removed
     */
    int sweepExpired();

    /**
     * @return a monotonic count of every entry this store deleted because it expired
     */
    long expiredEntries();

}
