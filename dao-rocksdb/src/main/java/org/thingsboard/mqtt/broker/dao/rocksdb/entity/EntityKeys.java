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
package org.thingsboard.mqtt.broker.dao.rocksdb.entity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * The key layouts of the {@code entity} and {@code entity_index} column families. They are part of the on-disk format,
 * pinned by {@code EntityKeysTest}.
 * <ul>
 *   <li>entity key: the type code, then the id (a UUID as 16 bytes, most significant long first, big-endian; a string
 *   as UTF-8);</li>
 *   <li>unique index key: the index code, the byte length of the UTF-8 value as 4 big-endian bytes, the value;</li>
 *   <li>non-unique index key: the same, then the id bytes.</li>
 * </ul>
 * The length in front of the value keeps the keys of one value apart from those of a longer value that starts with it.
 */
public final class EntityKeys {

    private EntityKeys() {
    }

    static byte[] uuidBytes(UUID id) {
        return ByteBuffer.allocate(Long.BYTES * 2).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    static byte[] stringBytes(String id) {
        return id.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The id written as {@code idBytes} in a key of {@code type}, as text: the UUID in its canonical form, or the string.
     */
    static String idText(StoredEntityType type, byte[] idBytes) {
        return switch (type.idKind()) {
            case UUID -> {
                ByteBuffer buffer = ByteBuffer.wrap(idBytes);
                yield new UUID(buffer.getLong(), buffer.getLong()).toString();
            }
            case STRING -> new String(idBytes, StandardCharsets.UTF_8);
        };
    }

    static byte[] entityKey(StoredEntityType type, byte[] id) {
        return ByteBuffer.allocate(1 + id.length).put(type.code()).put(id).array();
    }

    /**
     * The first key of {@code type}.
     */
    static byte[] typeLower(StoredEntityType type) {
        return new byte[]{type.code()};
    }

    /**
     * The first key after those of {@code type}.
     */
    static byte[] typeUpper(StoredEntityType type) {
        return new byte[]{(byte) (type.code() + 1)};
    }

    static byte[] uniqueIndexKey(EntityIndex index, String value) {
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(1 + Integer.BYTES + valueBytes.length)
                .put(index.code()).putInt(valueBytes.length).put(valueBytes).array();
    }

    static byte[] nonUniqueIndexKey(EntityIndex index, String value, byte[] id) {
        byte[] prefix = uniqueIndexKey(index, value);
        return ByteBuffer.allocate(prefix.length + id.length).put(prefix).put(id).array();
    }

    /**
     * The first key of the non-unique index entries of {@code value}.
     */
    static byte[] nonUniqueLower(EntityIndex index, String value) {
        return uniqueIndexKey(index, value);
    }

    /**
     * The first key after the non-unique index entries of {@code value}: the lower bound with its last byte that is
     * not {@code 0xFF} incremented and the bytes after it dropped.
     */
    static byte[] nonUniqueUpper(EntityIndex index, String value) {
        byte[] bound = uniqueIndexKey(index, value);
        int last = bound.length - 1;
        while (bound[last] == (byte) 0xFF) {
            last--;
        }
        byte[] upper = Arrays.copyOf(bound, last + 1);
        upper[last]++;
        return upper;
    }

}
