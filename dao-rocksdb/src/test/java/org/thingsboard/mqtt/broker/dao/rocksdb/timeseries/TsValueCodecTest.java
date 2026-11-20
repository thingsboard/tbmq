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

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.DoubleDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.KvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TsValueCodecTest {

    private static final String KEY = "incomingMsgs";

    @Test
    void aLongIsItsTypeAndEightBigEndianBytes() {
        assertThat(TsValueCodec.encode(new LongDataEntry(KEY, 42L))).containsExactly(2, 0, 0, 0, 0, 0, 0, 0, 42);
    }

    @Test
    void aLatestValueIsItsTimestampThenTheValue() {
        TsKvEntry latest = new BasicTsKvEntry(258, new LongDataEntry(KEY, 42L));

        assertThat(TsValueCodec.encodeLatest(latest)).containsExactly(
                0, 0, 0, 0, 0, 0, 1, 2,
                2, 0, 0, 0, 0, 0, 0, 0, 42);
    }

    @Test
    void aDoubleIsItsTypeAndTheBigEndianBitsOfTheDouble() {
        assertThat(TsValueCodec.encode(new DoubleDataEntry(KEY, 1.5))).containsExactly(3, 0x3F, 0xF8, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void eachTypeRoundTrips() {
        for (KvEntry kv : new KvEntry[]{
                new LongDataEntry(KEY, 42L), new LongDataEntry(KEY, Long.MIN_VALUE), new LongDataEntry(KEY, -1L),
                new DoubleDataEntry(KEY, 150.5), new DoubleDataEntry(KEY, -0.0), new DoubleDataEntry(KEY, Double.NaN)}) {
            assertThat(TsValueCodec.decode(KEY, TsValueCodec.encode(kv))).isEqualTo(kv);
        }
    }

    @Test
    void aValueWithoutAValueIsItsTypeAlone() {
        assertThat(TsValueCodec.encode(new LongDataEntry(KEY, null))).containsExactly(2);
        assertThat(TsValueCodec.decode(KEY, new byte[]{2})).isEqualTo(new LongDataEntry(KEY, null));
        assertThat(TsValueCodec.decode(KEY, TsValueCodec.encode(new DoubleDataEntry(KEY, null)))).isEqualTo(new DoubleDataEntry(KEY, null));
    }

    @Test
    void aLatestValueRoundTrips() {
        TsKvEntry latest = new BasicTsKvEntry(1_700_000_000_000L, new DoubleDataEntry(KEY, 0.25));

        assertThat(TsValueCodec.decodeLatest(KEY, TsValueCodec.encodeLatest(latest))).isEqualTo(latest);
    }

    @Test
    void theKeyOfTheDecodedValueIsTheOneGiven() {
        assertThat(TsValueCodec.decode("other", TsValueCodec.encode(new LongDataEntry(KEY, 1L))).getKey()).isEqualTo("other");
    }

    @Test
    void aTypeWithoutAnEntryClassIsRefused() {
        for (int type : new int[]{0, 1, 4, 5, 6}) {
            assertThatThrownBy(() -> TsValueCodec.decode(KEY, new byte[]{(byte) type, 1}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(String.valueOf(type));
        }
    }

}
