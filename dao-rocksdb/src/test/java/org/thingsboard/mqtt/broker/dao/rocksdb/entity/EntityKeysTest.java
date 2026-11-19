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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityKeysTest {

    @Test
    void aUuidEntityKeyIsTheTypeCodeThenSixteenBigEndianBytes() {
        UUID id = UUID.fromString("00000000-0000-0001-0000-000000000002");

        assertThat(EntityKeys.entityKey(StoredEntityType.USER, EntityKeys.uuidBytes(id)))
                .containsExactly(4, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2);
    }

    @Test
    void aStringEntityKeyIsTheTypeCodeThenTheUtf8Id() {
        assertThat(EntityKeys.entityKey(StoredEntityType.UNAUTHORIZED_CLIENT, "cé".getBytes(StandardCharsets.UTF_8)))
                .containsExactly(8, 'c', 0xC3, 0xA9);
    }

    @Test
    void idTextDecodesTheIdBytesOfEitherKind() {
        UUID id = UUID.randomUUID();

        assertThat(EntityKeys.idText(StoredEntityType.USER, EntityKeys.uuidBytes(id))).isEqualTo(id.toString());
        assertThat(EntityKeys.idText(StoredEntityType.UNAUTHORIZED_CLIENT, EntityKeys.stringBytes("cé"))).isEqualTo("cé");
    }

    @Test
    void idBytesIsTheReverseOfIdText() {
        UUID id = UUID.randomUUID();

        assertThat(EntityKeys.idBytes(StoredEntityType.USER, id.toString())).containsExactly(EntityKeys.uuidBytes(id));
        assertThat(EntityKeys.idBytes(StoredEntityType.UNAUTHORIZED_CLIENT, "cé")).containsExactly(EntityKeys.stringBytes("cé"));
    }

    @Test
    void theIdKindOfEachTypeDecidesItsEncoding() {
        for (StoredEntityType type : StoredEntityType.values()) {
            assertThat(type.idKind()).isEqualTo(type == StoredEntityType.UNAUTHORIZED_CLIENT
                    ? StoredEntityType.IdKind.STRING : StoredEntityType.IdKind.UUID);
        }
    }

    @Test
    void theRangeOfATypeCoversItsKeysAndNoOtherTypes() {
        byte[] lower = EntityKeys.typeLower(StoredEntityType.USER);
        byte[] upper = EntityKeys.typeUpper(StoredEntityType.USER);
        byte[] first = EntityKeys.entityKey(StoredEntityType.USER, new byte[16]);
        byte[] last = EntityKeys.entityKey(StoredEntityType.USER, filled(16, 0xFF));
        byte[] previousType = EntityKeys.entityKey(StoredEntityType.MQTT_CLIENT_CREDENTIALS, filled(16, 0xFF));
        byte[] nextType = EntityKeys.entityKey(StoredEntityType.USER_CREDENTIALS, new byte[16]);

        assertThat(lower).containsExactly(4);
        assertThat(upper).containsExactly(5);
        assertThat(inRange(first, lower, upper)).isTrue();
        assertThat(inRange(last, lower, upper)).isTrue();
        assertThat(inRange(previousType, lower, upper)).isFalse();
        assertThat(inRange(nextType, lower, upper)).isFalse();
    }

    @Test
    void aUniqueIndexKeyIsCodeLengthValue() {
        assertThat(EntityKeys.uniqueIndexKey(EntityIndex.USER_EMAIL, "a@b")).containsExactly(6, 0, 0, 0, 3, 'a', '@', 'b');
    }

    @Test
    void theLengthOfAnIndexValueCountsUtf8BytesNotCharacters() {
        assertThat(EntityKeys.uniqueIndexKey(EntityIndex.USER_EMAIL, "é")).containsExactly(6, 0, 0, 0, 2, 0xC3, 0xA9);
    }

    @Test
    void aNonUniqueIndexKeyIsTheUniqueKeyThenTheId() {
        byte[] id = EntityKeys.uuidBytes(UUID.fromString("00000000-0000-0001-0000-000000000002"));

        assertThat(EntityKeys.nonUniqueIndexKey(EntityIndex.CREDENTIALS_NAME, "ab", id))
                .containsExactly(4, 0, 0, 0, 2, 'a', 'b', 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2);
    }

    @Test
    void nonUniqueRangesOfAShorterValueNeverCoverALongerOne() {
        byte[] id = new byte[16];
        byte[] lower = EntityKeys.nonUniqueLower(EntityIndex.CREDENTIALS_NAME, "ab");
        byte[] upper = EntityKeys.nonUniqueUpper(EntityIndex.CREDENTIALS_NAME, "ab");

        assertThat(inRange(EntityKeys.nonUniqueIndexKey(EntityIndex.CREDENTIALS_NAME, "ab", id), lower, upper)).isTrue();
        assertThat(inRange(EntityKeys.nonUniqueIndexKey(EntityIndex.CREDENTIALS_NAME, "ab", filled(16, 0xFF)), lower, upper)).isTrue();
        assertThat(inRange(EntityKeys.nonUniqueIndexKey(EntityIndex.CREDENTIALS_NAME, "abc", id), lower, upper)).isFalse();
        assertThat(inRange(EntityKeys.nonUniqueIndexKey(EntityIndex.CREDENTIALS_NAME, "a", id), lower, upper)).isFalse();
        assertThat(inRange(EntityKeys.nonUniqueIndexKey(EntityIndex.CREDENTIALS_NAME, "", id), lower, upper)).isFalse();
        assertThat(inRange(EntityKeys.nonUniqueIndexKey(EntityIndex.CREDENTIALS_TYPE, "ab", id), lower, upper)).isFalse();
    }

    @Test
    void theRangeOfAnEmptyValueHoldsOnlyEmptyValueKeys() {
        byte[] lower = EntityKeys.nonUniqueLower(EntityIndex.CREDENTIALS_NAME, "");
        byte[] upper = EntityKeys.nonUniqueUpper(EntityIndex.CREDENTIALS_NAME, "");

        assertThat(inRange(EntityKeys.nonUniqueIndexKey(EntityIndex.CREDENTIALS_NAME, "", new byte[16]), lower, upper)).isTrue();
        assertThat(inRange(EntityKeys.nonUniqueIndexKey(EntityIndex.CREDENTIALS_NAME, "a", new byte[16]), lower, upper)).isFalse();
    }

    @Test
    void theUpperBoundIncrementsTheLastByte() {
        assertThat(EntityKeys.upperBound(new byte[]{4, 0, 0, 0, 2, 'a', 'b'})).containsExactly(4, 0, 0, 0, 2, 'a', 'c');
        assertThat(EntityKeys.upperBound(new byte[]{4, 0, 0, 0, 0})).containsExactly(4, 0, 0, 0, 1);
    }

    @Test
    void theUpperBoundCarriesOverFullBytes() {
        byte[] prefix = {4, 0x7F, (byte) 0xFF, (byte) 0xFF};
        byte[] upper = EntityKeys.upperBound(prefix);

        assertThat(upper).containsExactly(4, 0x80);
        assertThat(inRange(prefix, prefix, upper)).isTrue();
        assertThat(inRange(new byte[]{4, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 1}, prefix, upper)).isTrue();
        assertThat(inRange(new byte[]{4, (byte) 0x80}, prefix, upper)).isFalse();
    }

    @Test
    void aPrefixOfFullBytesOnlyHasNoUpperBound() {
        assertThatThrownBy(() -> EntityKeys.upperBound(new byte[]{(byte) 0xFF, (byte) 0xFF}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyCodeIsUniqueAndNoneIsZero() {
        Set<Integer> typeCodes = new HashSet<>();
        for (StoredEntityType type : StoredEntityType.values()) {
            assertThat(type.code()).isNotZero();
            assertThat(typeCodes.add((int) type.code())).as("type code of %s", type).isTrue();
        }
        Set<Integer> indexCodes = new HashSet<>();
        for (EntityIndex index : EntityIndex.values()) {
            assertThat(index.code()).isNotZero();
            assertThat(indexCodes.add((int) index.code())).as("index code of %s", index).isTrue();
        }
    }

    @Test
    void theTypeCodesAreThePinnedOnes() {
        assertThat(StoredEntityType.ADMIN_SETTINGS.code()).isEqualTo((byte) 1);
        assertThat(StoredEntityType.MQTT_AUTH_PROVIDER.code()).isEqualTo((byte) 2);
        assertThat(StoredEntityType.MQTT_CLIENT_CREDENTIALS.code()).isEqualTo((byte) 3);
        assertThat(StoredEntityType.USER.code()).isEqualTo((byte) 4);
        assertThat(StoredEntityType.USER_CREDENTIALS.code()).isEqualTo((byte) 5);
        assertThat(StoredEntityType.WEBSOCKET_CONNECTION.code()).isEqualTo((byte) 6);
        assertThat(StoredEntityType.WEBSOCKET_SUBSCRIPTION.code()).isEqualTo((byte) 7);
        assertThat(StoredEntityType.UNAUTHORIZED_CLIENT.code()).isEqualTo((byte) 8);
        assertThat(StoredEntityType.values()).hasSize(8);
    }

    @Test
    void theIndexCodesAndConstraintNamesArePinned() {
        assertIndex(EntityIndex.ADMIN_SETTINGS_KEY, 1, "admin_settings_key_unq_key");
        assertIndex(EntityIndex.AUTH_PROVIDER_TYPE, 2, "mqtt_auth_provider_type_key");
        assertIndex(EntityIndex.CREDENTIALS_ID, 3, "mqtt_client_credentials_id_unq_key");
        assertIndex(EntityIndex.CREDENTIALS_NAME, 4, null);
        assertIndex(EntityIndex.CREDENTIALS_TYPE, 5, null);
        assertIndex(EntityIndex.USER_EMAIL, 6, "broker_user_email_key");
        assertIndex(EntityIndex.USER_CREDENTIALS_USER_ID, 7, "user_credentials_user_id_key");
        assertIndex(EntityIndex.USER_CREDENTIALS_ACTIVATE_TOKEN, 8, "user_credentials_activate_token_key");
        assertIndex(EntityIndex.USER_CREDENTIALS_RESET_TOKEN, 9, "user_credentials_reset_token_key");
        assertIndex(EntityIndex.WS_CONNECTION_USER_NAME, 10, "name_unq_key");
        assertIndex(EntityIndex.WS_CONNECTION_USER, 11, null);
        assertIndex(EntityIndex.WS_SUBSCRIPTION_CONNECTION, 12, null);
        assertIndex(EntityIndex.TS_KEY_DICTIONARY, 64, null);
        assertThat(EntityIndex.values()).hasSize(13);
    }

    private static void assertIndex(EntityIndex index, int code, String constraintName) {
        assertThat(index.code()).as("code of %s", index).isEqualTo((byte) code);
        assertThat(index.constraintName()).as("constraint of %s", index).isEqualTo(constraintName);
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private static boolean inRange(byte[] key, byte[] lowerInclusive, byte[] upperExclusive) {
        return Arrays.compareUnsigned(key, lowerInclusive) >= 0 && Arrays.compareUnsigned(key, upperExclusive) < 0;
    }

}
