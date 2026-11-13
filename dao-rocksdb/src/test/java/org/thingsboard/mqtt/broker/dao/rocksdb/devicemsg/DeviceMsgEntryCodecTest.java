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

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceMsgEntryCodecTest {

    @Test
    void headerAndBodyRoundTrip() {
        byte[] v = DeviceMsgEntryCodec.encode(65_535, PersistedPacketType.PUBLISH, 1_000L, 2_000L, 3_000L, new byte[]{1, 2, 3});

        var h = DeviceMsgEntryCodec.header(v);

        assertThat(h.packetId()).isEqualTo(65_535);
        assertThat(h.packetType()).isEqualTo(PersistedPacketType.PUBLISH);
        assertThat(h.createdTimeMs()).isEqualTo(1_000L);
        assertThat(h.msgExpiresAtMs()).isEqualTo(2_000L);
        assertThat(h.ttlExpiresAtMs()).isEqualTo(3_000L);
        assertThat(DeviceMsgEntryCodec.body(v)).containsExactly(1, 2, 3);
    }

    // Trello #652: the PUBREL update must not lose expiry data
    @Test
    void packetTypeUpdateKeepsBothExpiryTimes() {
        byte[] v = DeviceMsgEntryCodec.encode(9, PersistedPacketType.PUBLISH, 1L, 2L, 3L, new byte[]{7});

        byte[] updated = DeviceMsgEntryCodec.withPacketType(v, PersistedPacketType.PUBREL);
        var h = DeviceMsgEntryCodec.header(updated);

        assertThat(h.packetType()).isEqualTo(PersistedPacketType.PUBREL);
        assertThat(h.packetId()).isEqualTo(9);
        assertThat(h.createdTimeMs()).isEqualTo(1L);
        assertThat(h.msgExpiresAtMs()).isEqualTo(2L);
        assertThat(h.ttlExpiresAtMs()).isEqualTo(3L);
        assertThat(DeviceMsgEntryCodec.body(updated)).containsExactly(7);
        assertThat(DeviceMsgEntryCodec.header(v).packetType()).isEqualTo(PersistedPacketType.PUBLISH);
    }

    @Test
    void anEmptyBodyRoundTrips() {
        byte[] v = DeviceMsgEntryCodec.encode(1, PersistedPacketType.PUBREL, 0L, 0L, 0L, new byte[0]);

        assertThat(DeviceMsgEntryCodec.header(v).packetType()).isEqualTo(PersistedPacketType.PUBREL);
        assertThat(DeviceMsgEntryCodec.body(v)).isEmpty();
    }

    // the layout is the on-disk format: a change to it or to PersistedPacketType must not pass unnoticed
    @Test
    void theEncodedBytesArePinned() {
        byte[] publish = DeviceMsgEntryCodec.encode(0x0102, PersistedPacketType.PUBLISH, 3L, 4L, 5L, new byte[]{9});

        assertThat(publish).containsExactly(
                1,                      // format version
                0,                      // packet type: PUBLISH
                1, 2,                   // packet id
                0, 0, 0, 0, 0, 0, 0, 3, // created time
                0, 0, 0, 0, 0, 0, 0, 4, // message expiry time
                0, 0, 0, 0, 0, 0, 0, 5, // TTL expiry time
                9);                     // body
        assertThat(DeviceMsgEntryCodec.encode(1, PersistedPacketType.PUBREL, 0L, 0L, 0L, new byte[0])[1]).isEqualTo((byte) 1);
        assertThat(DeviceMsgEntryCodec.withPacketType(publish, PersistedPacketType.PUBREL)[1]).isEqualTo((byte) 1);
    }

    @Test
    void anUnknownPacketTypeIsRejected() {
        byte[] v = DeviceMsgEntryCodec.encode(1, PersistedPacketType.PUBLISH, 0L, 0L, 0L, new byte[0]);
        v[1] = 2;

        assertThatThrownBy(() -> DeviceMsgEntryCodec.header(v))
                .isInstanceOf(StoreException.class)
                .hasMessageContaining("Unknown packet type 2");
    }

    @Test
    void aTruncatedValueIsRejected() {
        assertThatThrownBy(() -> DeviceMsgEntryCodec.header(new byte[5])).isInstanceOf(StoreException.class);
    }

}
