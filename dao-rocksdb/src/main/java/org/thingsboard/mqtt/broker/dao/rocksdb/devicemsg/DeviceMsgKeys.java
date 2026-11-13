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

import com.google.common.hash.Hashing;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Key layout of the {@code device_msg} column family: an 8-byte hash of the queue key, the 4-byte length of the queue
 * key, the queue key in UTF-8 and the 8-byte sequence, all big-endian. The length keeps the prefix of a shorter queue
 * key from covering a longer one. The {@code device_msg_meta} key is the queue key in UTF-8.
 */
public final class DeviceMsgKeys {

    private static final int HASH_LENGTH = Long.BYTES;
    private static final int LENGTH_LENGTH = Integer.BYTES;

    private DeviceMsgKeys() {
    }

    public static byte[] entryKey(String queueKey, long sequence) {
        byte[] id = utf8(queueKey);
        return ByteBuffer.allocate(prefixLength(id) + Long.BYTES)
                .put(prefix(id)).putLong(sequence).array();
    }

    public static byte[] queuePrefix(String queueKey) {
        return prefix(utf8(queueKey));
    }

    /**
     * The exclusive upper bound of the entries of a queue, for range scans and range deletes.
     */
    public static byte[] queueEnd(String queueKey) {
        byte[] id = utf8(queueKey);
        byte[] end = Arrays.copyOf(prefix(id), prefixLength(id) + Long.BYTES);
        Arrays.fill(end, prefixLength(id), end.length, (byte) 0xFF);
        return end;
    }

    public static long sequenceOf(byte[] entryKey) {
        return ByteBuffer.wrap(entryKey).getLong(entryKey.length - Long.BYTES);
    }

    public static byte[] metaKey(String queueKey) {
        return utf8(queueKey);
    }

    private static byte[] prefix(byte[] id) {
        return ByteBuffer.allocate(prefixLength(id))
                .putLong(Hashing.murmur3_128().hashBytes(id).asLong())
                .putInt(id.length)
                .put(id).array();
    }

    private static int prefixLength(byte[] id) {
        return HASH_LENGTH + LENGTH_LENGTH + id.length;
    }

    private static byte[] utf8(String queueKey) {
        return queueKey.getBytes(StandardCharsets.UTF_8);
    }

}
