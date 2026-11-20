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
package org.thingsboard.mqtt.broker.dao.rocksdb.timeseries;

import org.thingsboard.mqtt.broker.dao.rocksdb.entity.EntityKeys;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The key layouts of the {@code ts} and {@code ts_latest} column families. They are part of the on-disk format, pinned
 * by {@code TsKeysTest}.
 * <ul>
 *   <li>{@code ts_latest} key: the byte length of the UTF-8 entity id as 4 big-endian bytes, the entity id, the key id
 *   of the time series key as 4 big-endian bytes;</li>
 *   <li>{@code ts} key: the same, then the timestamp as 8 big-endian bytes.</li>
 * </ul>
 * The length in front of the entity id keeps the keys of one entity apart from those of a longer entity id that starts
 * with it. The timestamp sorts as an unsigned number, so a negative one is refused and a negative range bound reads as
 * zero.
 */
public final class TsKeys {

    private static final int LENGTH_BYTES = Integer.BYTES;
    private static final int KEY_ID_BYTES = Integer.BYTES;
    private static final int TS_BYTES = Long.BYTES;

    private TsKeys() {
    }

    /**
     * @throws IllegalArgumentException for a negative {@code ts}
     */
    static byte[] tsKey(String entityId, int keyId, long ts) {
        if (ts < 0) {
            throw new IllegalArgumentException("A time series entry cannot have a negative timestamp: " + ts);
        }
        return withTs(latestKey(entityId, keyId), ts);
    }

    /**
     * The first key of the entries of the pair at or after {@code fromTsInclusive}.
     */
    static byte[] tsLower(String entityId, int keyId, long fromTsInclusive) {
        return withTs(latestKey(entityId, keyId), Math.max(0, fromTsInclusive));
    }

    /**
     * The first key after the entries of the pair before {@code toTsExclusive}.
     */
    static byte[] tsUpper(String entityId, int keyId, long toTsExclusive) {
        return withTs(latestKey(entityId, keyId), Math.max(0, toTsExclusive));
    }

    static byte[] latestKey(String entityId, int keyId) {
        byte[] entity = entityId.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(LENGTH_BYTES + entity.length + KEY_ID_BYTES)
                .putInt(entity.length).put(entity).putInt(keyId).array();
    }

    /**
     * The first key of the latest values of the entity.
     */
    static byte[] latestEntityLower(String entityId) {
        byte[] entity = entityId.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(LENGTH_BYTES + entity.length).putInt(entity.length).put(entity).array();
    }

    /**
     * The first key after the latest values of the entity.
     */
    static byte[] latestEntityUpper(String entityId) {
        return EntityKeys.upperBound(latestEntityLower(entityId));
    }

    /**
     * The key id of a {@code ts_latest} key: its last 4 bytes.
     */
    static int keyIdOfLatest(byte[] latestKey) {
        return ByteBuffer.wrap(latestKey, latestKey.length - KEY_ID_BYTES, KEY_ID_BYTES).getInt();
    }

    /**
     * The timestamp of a {@code ts} key: its last 8 bytes.
     */
    static long tsOf(byte[] tsKey) {
        return ByteBuffer.wrap(tsKey, tsKey.length - TS_BYTES, TS_BYTES).getLong();
    }

    /**
     * The part of a {@code ts} key that names its (entity, key) pair: the key without its timestamp, which is the
     * {@code ts_latest} key of the pair.
     */
    static byte[] pairOf(byte[] tsKey) {
        return Arrays.copyOf(tsKey, tsKey.length - TS_BYTES);
    }

    /**
     * The {@code ts} key of {@code ts} in the pair named by {@code pair}, as {@link #pairOf} returns it.
     */
    static byte[] withTs(byte[] pair, long ts) {
        return ByteBuffer.allocate(pair.length + TS_BYTES).put(pair).putLong(ts).array();
    }

}
