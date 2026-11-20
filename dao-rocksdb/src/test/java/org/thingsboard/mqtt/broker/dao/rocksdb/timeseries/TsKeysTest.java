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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TsKeysTest {

    @Test
    void aTsKeyIsTheLengthPrefixedEntityIdTheKeyIdAndTheTs() {
        assertThat(TsKeys.tsKey("ab", 1, 258)).containsExactly(
                0, 0, 0, 2, 'a', 'b',
                0, 0, 0, 1,
                0, 0, 0, 0, 0, 0, 1, 2);
    }

    @Test
    void aLatestKeyIsTheLengthPrefixedEntityIdAndTheKeyId() {
        assertThat(TsKeys.latestKey("ab", 300)).containsExactly(
                0, 0, 0, 2, 'a', 'b',
                0, 0, 1, 44);
    }

    @Test
    void theEntityIdIsWrittenAsUtf8() {
        byte[] key = TsKeys.latestKey("ї", 1);

        assertThat(key).containsExactly(0, 0, 0, 2, 0xD1, 0x97, 0, 0, 0, 1);
    }

    @Test
    void theRangesOfAnEntityNeverCoverALongerEntityThatStartsWithIt() {
        byte[] abLower = TsKeys.latestEntityLower("ab");
        byte[] abUpper = TsKeys.latestEntityUpper("ab");

        for (byte[] abc : new byte[][]{TsKeys.latestKey("abc", 1), TsKeys.tsKey("abc", 1, 0), TsKeys.tsKey("abc", 0, Long.MAX_VALUE)}) {
            assertThat(inRange(abc, abLower, abUpper)).isFalse();
        }
        assertThat(inRange(TsKeys.latestKey("ab", Integer.MAX_VALUE), abLower, abUpper)).isTrue();
        assertThat(inRange(TsKeys.latestKey("ab", 0), abLower, abUpper)).isTrue();
        // the time series range of "ab" is as clear of "abc"
        assertThat(inRange(TsKeys.tsKey("abc", 1, 5), TsKeys.tsLower("ab", 1, 0), TsKeys.tsUpper("ab", 1, Long.MAX_VALUE))).isFalse();
    }

    @Test
    void aTsRangeHoldsTheTimestampsFromItsStartUpToItsEnd() {
        byte[] lower = TsKeys.tsLower("e", 7, 100);
        byte[] upper = TsKeys.tsUpper("e", 7, 200);

        assertThat(inRange(TsKeys.tsKey("e", 7, 99), lower, upper)).isFalse();
        assertThat(inRange(TsKeys.tsKey("e", 7, 100), lower, upper)).isTrue();
        assertThat(inRange(TsKeys.tsKey("e", 7, 199), lower, upper)).isTrue();
        assertThat(inRange(TsKeys.tsKey("e", 7, 200), lower, upper)).isFalse();
        assertThat(inRange(TsKeys.tsKey("e", 6, 150), lower, upper)).isFalse();
        assertThat(inRange(TsKeys.tsKey("e", 8, 150), lower, upper)).isFalse();
    }

    @Test
    void aNegativeRangeBoundIsTheStartOfTheKey() {
        assertThat(TsKeys.tsLower("e", 7, -5)).isEqualTo(TsKeys.tsKey("e", 7, 0));
        assertThat(TsKeys.tsUpper("e", 7, -1)).isEqualTo(TsKeys.tsKey("e", 7, 0));
    }

    @Test
    void aNegativeTimestampIsRefused() {
        assertThatThrownBy(() -> TsKeys.tsKey("e", 7, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void theTimestampAndThePairAreReadBackFromATsKey() {
        byte[] key = TsKeys.tsKey("клієнт", 9, 1_700_000_000_000L);

        assertThat(TsKeys.tsOf(key)).isEqualTo(1_700_000_000_000L);
        assertThat(TsKeys.pairOf(key)).isEqualTo(TsKeys.latestKey("клієнт", 9));
        assertThat(TsKeys.withTs(TsKeys.pairOf(key), 5)).isEqualTo(TsKeys.tsKey("клієнт", 9, 5));
        assertThat(TsKeys.keyIdOfLatest(TsKeys.latestKey("клієнт", 9))).isEqualTo(9);
    }

    private static boolean inRange(byte[] key, byte[] lowerInclusive, byte[] upperExclusive) {
        return Arrays.compareUnsigned(key, lowerInclusive) >= 0 && Arrays.compareUnsigned(key, upperExclusive) < 0;
    }

}
