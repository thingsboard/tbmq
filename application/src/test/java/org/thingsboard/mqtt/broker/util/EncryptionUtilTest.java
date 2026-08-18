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
package org.thingsboard.mqtt.broker.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the SHA3-256 hex encoding produced by {@link EncryptionUtil}. The hashes are persisted and compared
 * (e.g. for X.509 credentials), so the encoding must stay lowercase, zero-padded and separator-free across
 * BouncyCastle upgrades — the expected values below were captured before the hex encoder was swapped.
 */
public class EncryptionUtilTest {

    @Test
    public void givenEmptyString_whenGetSha3Hash_thenReturnsKnownSha3OfEmptyInput() {
        assertThat(EncryptionUtil.getSha3Hash(""))
                .isEqualTo("a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a");
    }

    @Test
    public void givenPlainString_whenGetSha3Hash_thenReturnsLowercaseHexDigest() {
        assertThat(EncryptionUtil.getSha3Hash("test"))
                .isEqualTo("36f028580bb02cc8272a9a020f4200e346e276ae664e45ee80745574e2f5ab80");
    }

    @Test
    public void givenWrappedCert_whenGetSha3Hash_thenPemMarkersAreStrippedBeforeHashing() {
        String wrapped = "-----BEGIN CERTIFICATE-----\nAQID\n-----END CERTIFICATE-----";

        assertThat(EncryptionUtil.getSha3Hash(wrapped))
                .isEqualTo("a055efda602931f78b0a080e1ea91cb81e5c616a080337fbd7507dcb9bbd346b")
                .isEqualTo(EncryptionUtil.getSha3Hash("AQID"));
    }

    @Test
    public void givenTokens_whenGetSha3Hash_thenTokensAreJoinedWithDelimiter() {
        assertThat(EncryptionUtil.getSha3Hash("|", "a", "b"))
                .isEqualTo("164b8bb3c8e98ac3aa98fd28d0dde0f446b359983433947f346907246a385e27");
    }

    @Test
    public void givenBlankAndNullTokens_whenGetSha3Hash_thenTheyAreSkippedWithoutExtraDelimiters() {
        assertThat(EncryptionUtil.getSha3Hash("|", "a", "", null, "b"))
                .isEqualTo(EncryptionUtil.getSha3Hash("|", "a", "b"));
    }

    @Test
    public void givenDigest_whenGetSha3Hash_thenHexIsAlwaysSixtyFourLowercaseChars() {
        assertThat(EncryptionUtil.getSha3Hash("any-payload")).matches("[0-9a-f]{64}");
    }
}
