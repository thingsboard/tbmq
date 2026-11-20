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

import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.DoubleDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.KvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;

import java.nio.ByteBuffer;

/**
 * The value layouts of the {@code ts} and {@code ts_latest} column families, part of the on-disk format and pinned by
 * {@code TsValueCodecTest}.
 * <ul>
 *   <li>{@code ts} value: the type byte, then the value: a long as 8 big-endian bytes, a double as the 8 big-endian
 *   bytes of its bits; a {@code null} value is the type byte alone;</li>
 *   <li>{@code ts_latest} value: the timestamp as 8 big-endian bytes, then the {@code ts} value.</li>
 * </ul>
 * The type codes are BOOLEAN 1, LONG 2, DOUBLE 3, STRING 4 and JSON 5. The broker has entry classes for LONG and DOUBLE
 * only, so the other codes are reserved: a value of a reserved type is refused when it is read.
 */
public final class TsValueCodec {

    static final byte LONG = 2;
    static final byte DOUBLE = 3;

    private static final int TYPE_BYTES = 1;
    private static final int VALUE_BYTES = Long.BYTES;
    private static final int TS_BYTES = Long.BYTES;

    private TsValueCodec() {
    }

    static byte[] encode(KvEntry kv) {
        return switch (kv.getDataType()) {
            case LONG -> encode(LONG, kv.getLongValue().map(Long::longValue).orElse(null));
            case DOUBLE -> encode(DOUBLE, kv.getDoubleValue().map(Double::doubleToRawLongBits).orElse(null));
        };
    }

    /**
     * @throws IllegalStateException for a value of a reserved or unknown type, or of a wrong length
     */
    static KvEntry decode(String key, byte[] value) {
        return decode(key, value, 0);
    }

    static byte[] encodeLatest(TsKvEntry e) {
        byte[] value = encode(e);
        return ByteBuffer.allocate(TS_BYTES + value.length).putLong(e.getTs()).put(value).array();
    }

    /**
     * @throws IllegalStateException for a value of a reserved or unknown type, or of a wrong length
     */
    static TsKvEntry decodeLatest(String key, byte[] value) {
        long ts = ByteBuffer.wrap(value, 0, TS_BYTES).getLong();
        return new BasicTsKvEntry(ts, decode(key, value, TS_BYTES));
    }

    private static byte[] encode(byte type, Long bits) {
        if (bits == null) {
            return new byte[]{type};
        }
        return ByteBuffer.allocate(TYPE_BYTES + VALUE_BYTES).put(type).putLong(bits).array();
    }

    private static KvEntry decode(String key, byte[] value, int offset) {
        byte type = value[offset];
        int length = value.length - offset;
        if (type != LONG && type != DOUBLE) {
            throw new IllegalStateException("A time series value of key " + key + " has the unsupported type " + type);
        }
        if (length == TYPE_BYTES) {
            return type == LONG ? new LongDataEntry(key, null) : new DoubleDataEntry(key, null);
        }
        if (length != TYPE_BYTES + VALUE_BYTES) {
            throw new IllegalStateException("A time series value of key " + key + " has " + length + " bytes");
        }
        long bits = ByteBuffer.wrap(value, offset + TYPE_BYTES, VALUE_BYTES).getLong();
        return type == LONG ? new LongDataEntry(key, bits) : new DoubleDataEntry(key, Double.longBitsToDouble(bits));
    }

}
