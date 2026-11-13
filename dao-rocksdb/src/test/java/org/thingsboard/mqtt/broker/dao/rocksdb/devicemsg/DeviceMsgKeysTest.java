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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceMsgKeysTest {

    @Test
    void prefixOfAShorterIdNeverCoversALongerId() {
        byte[] abEnd = DeviceMsgKeys.queueEnd("ab");
        byte[] abc1 = DeviceMsgKeys.entryKey("abc", 1);

        assertThat(startsWith(abc1, DeviceMsgKeys.queuePrefix("ab"))).isFalse();
        assertThat(Arrays.compareUnsigned(DeviceMsgKeys.entryKey("ab", Long.MAX_VALUE), abEnd)).isLessThan(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a/b", "a_b", "ss_group_sensors/+/temp", "клієнт-1", "x\u0000y"})
    void sequencesOfOneQueueSortInOrderAndStayInsideItsRange(String id) {
        byte[] k1 = DeviceMsgKeys.entryKey(id, 1);
        byte[] k2 = DeviceMsgKeys.entryKey(id, 2);
        byte[] k256 = DeviceMsgKeys.entryKey(id, 256);

        assertThat(Arrays.compareUnsigned(k1, k2)).isLessThan(0);
        assertThat(Arrays.compareUnsigned(k2, k256)).isLessThan(0);
        assertThat(DeviceMsgKeys.sequenceOf(k256)).isEqualTo(256);
        assertThat(startsWith(k256, DeviceMsgKeys.queuePrefix(id))).isTrue();
        assertThat(Arrays.compareUnsigned(k256, DeviceMsgKeys.queueEnd(id))).isLessThan(0);
        assertThat(Arrays.compareUnsigned(DeviceMsgKeys.queuePrefix(id), k1)).isLessThan(0);
    }

    @Test
    void aVeryLongIdRoundTrips() {
        String id = "c".repeat(65_535);

        assertThat(DeviceMsgKeys.sequenceOf(DeviceMsgKeys.entryKey(id, 7))).isEqualTo(7);
    }

    @Test
    void theMetaKeyIsTheUtf8QueueKey() {
        assertThat(DeviceMsgKeys.metaKey("клієнт-1")).isEqualTo("клієнт-1".getBytes(StandardCharsets.UTF_8));
    }

    private static boolean startsWith(byte[] key, byte[] prefix) {
        return key.length >= prefix.length && Arrays.equals(key, 0, prefix.length, prefix, 0, prefix.length);
    }

}
