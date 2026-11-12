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
package org.thingsboard.mqtt.broker.dao.rocksdb.devicemsg;

import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.ByteBuffer;

/**
 * The value of {@code device_msg_meta}: the bookkeeping of one queue. {@code earliestMsgExpiryMs} is {@code 0} when
 * no entry expires. Fixed 44-byte big-endian layout: version (1, value 1), first sequence (8), last sequence (8),
 * last packet id (4), count (4), sent-up-to (8), earliest message expiry (8), 3 reserved zero bytes.
 */
public record DeviceQueueMeta(long firstSequence, long lastSequence, int lastPacketId, int count, long sentUpTo,
                              long earliestMsgExpiryMs) {

    static final int LENGTH = 44;
    private static final byte FORMAT_VERSION = 1;

    public static DeviceQueueMeta empty() {
        return new DeviceQueueMeta(0, 0, 0, 0, 0, 0);
    }

    public byte[] toBytes() {
        return ByteBuffer.allocate(LENGTH)
                .put(FORMAT_VERSION)
                .putLong(firstSequence)
                .putLong(lastSequence)
                .putInt(lastPacketId)
                .putInt(count)
                .putLong(sentUpTo)
                .putLong(earliestMsgExpiryMs).array();
    }

    public static DeviceQueueMeta fromBytes(byte[] bytes) {
        if (bytes.length != LENGTH) {
            throw new StoreException("A device queue meta of " + bytes.length + " bytes, expected " + LENGTH);
        }
        if (bytes[0] != FORMAT_VERSION) {
            throw new StoreException("Unsupported device queue meta format version " + bytes[0]);
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.position(1);
        return new DeviceQueueMeta(buf.getLong(), buf.getLong(), buf.getInt(), buf.getInt(), buf.getLong(), buf.getLong());
    }

}
