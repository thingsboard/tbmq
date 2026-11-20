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
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
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

    /**
     * The UTF-8 bytes of a string id. An id that UTF-8 cannot encode (a lone surrogate) is refused: {@code getBytes}
     * would write it as {@code ?}, so two ids that differ as strings would share one key while the projection keeps
     * them apart.
     *
     * @throws IllegalArgumentException for an id with a lone surrogate
     */
    static byte[] stringBytes(String id) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(id));
            return Arrays.copyOfRange(encoded.array(), encoded.arrayOffset() + encoded.position(),
                    encoded.arrayOffset() + encoded.limit());
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("The id " + id + " is not valid UTF-16: it has a lone surrogate", e);
        }
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

    /**
     * The id bytes of {@code idText} in a key of {@code type}, the reverse of {@link #idText}.
     */
    static byte[] idBytes(StoredEntityType type, String idText) {
        return switch (type.idKind()) {
            case UUID -> uuidBytes(UUID.fromString(idText));
            case STRING -> stringBytes(idText);
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

    /**
     * The key of the entry of {@code value} in the unique {@code index}. Public for the time series key dictionary,
     * which writes its own entries with it.
     */
    public static byte[] uniqueIndexKey(EntityIndex index, String value) {
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
     * The first key after the non-unique index entries of {@code value}.
     */
    static byte[] nonUniqueUpper(EntityIndex index, String value) {
        return upperBound(uniqueIndexKey(index, value));
    }

    /**
     * The first key after every key that starts with {@code prefix}: the prefix with its last byte that is not
     * {@code 0xFF} incremented and the bytes after it dropped. An index key never ends in {@code 0xFF} (UTF-8 has no
     * such byte, and an empty value ends in its zero length), so the carry only keeps the bound right for any prefix.
     * Public for the time series keys, whose key ids can end in {@code 0xFF}.
     *
     * @throws IllegalArgumentException for a prefix of {@code 0xFF} bytes only, which has no upper bound
     */
    public static byte[] upperBound(byte[] prefix) {
        int last = prefix.length - 1;
        while (last >= 0 && prefix[last] == (byte) 0xFF) {
            last--;
        }
        if (last < 0) {
            throw new IllegalArgumentException("A prefix of 0xFF bytes only has no upper bound");
        }
        byte[] upper = Arrays.copyOf(prefix, last + 1);
        upper[last]++;
        return upper;
    }

}
