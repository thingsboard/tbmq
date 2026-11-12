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

import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Value layout of a {@code device_msg} entry: 1 byte format version, 1 byte packet type (0 PUBLISH, 1 PUBREL),
 * 2 bytes packet id, 8 bytes created time, 8 bytes message expiry time, 8 bytes TTL expiry time, then the body.
 * The time fields are epoch milliseconds, {@code 0} meaning none.
 */
public final class DeviceMsgEntryCodec {

    public static final int HEADER_LENGTH = 28;

    private static final byte FORMAT_VERSION = 1;
    private static final int TYPE_OFFSET = 1;
    private static final int PACKET_ID_OFFSET = 2;
    private static final int CREATED_OFFSET = 4;
    private static final int MSG_EXPIRY_OFFSET = 12;
    private static final int TTL_EXPIRY_OFFSET = 20;

    private static final PersistedPacketType[] TYPES = PersistedPacketType.values();

    public record Header(int packetId, PersistedPacketType packetType, long createdTimeMs, long msgExpiresAtMs,
                         long ttlExpiresAtMs) {
    }

    private DeviceMsgEntryCodec() {
    }

    public static byte[] encode(int packetId, PersistedPacketType type, long createdTimeMs, long msgExpiresAtMs,
                                long ttlExpiresAtMs, byte[] body) {
        return ByteBuffer.allocate(HEADER_LENGTH + body.length)
                .put(FORMAT_VERSION)
                .put((byte) type.ordinal())
                .putShort((short) packetId)
                .putLong(createdTimeMs)
                .putLong(msgExpiresAtMs)
                .putLong(ttlExpiresAtMs)
                .put(body).array();
    }

    public static Header header(byte[] value) {
        checkHeader(value);
        ByteBuffer buf = ByteBuffer.wrap(value);
        return new Header(Short.toUnsignedInt(buf.getShort(PACKET_ID_OFFSET)),
                packetType(value[TYPE_OFFSET]),
                buf.getLong(CREATED_OFFSET),
                buf.getLong(MSG_EXPIRY_OFFSET),
                buf.getLong(TTL_EXPIRY_OFFSET));
    }

    public static byte[] body(byte[] value) {
        checkHeader(value);
        return Arrays.copyOfRange(value, HEADER_LENGTH, value.length);
    }

    /**
     * @return a copy of the entry with the packet type replaced; every other field and the body are untouched
     */
    public static byte[] withPacketType(byte[] value, PersistedPacketType type) {
        checkHeader(value);
        byte[] copy = value.clone();
        copy[TYPE_OFFSET] = (byte) type.ordinal();
        return copy;
    }

    private static PersistedPacketType packetType(byte code) {
        if (code < 0 || code >= TYPES.length) {
            throw new StoreException("Unknown packet type " + code + " in a device queue entry");
        }
        return TYPES[code];
    }

    private static void checkHeader(byte[] value) {
        if (value.length < HEADER_LENGTH) {
            throw new StoreException("A device queue entry of " + value.length + " bytes is shorter than its header");
        }
        if (value[0] != FORMAT_VERSION) {
            throw new StoreException("Unsupported device queue entry format version " + value[0]);
        }
    }

}
