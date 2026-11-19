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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class ProjectionIndexTest {

    private final ProjectionIndex<MqttClientCredentials> index = new ProjectionIndex<>(EntityDescriptors.MQTT_CLIENT_CREDENTIALS.type(), EntityDescriptors.MQTT_CLIENT_CREDENTIALS.projection());
    private final Map<Object, String> namesById = new HashMap<>();

    @Test
    void enumsSortByTheirNameAsTheSqlStringColumnDoes() {
        put(credentials("device", ClientType.DEVICE));
        put(credentials("integration", ClientType.INTEGRATION));
        put(credentials("application", ClientType.APPLICATION));

        assertThat(names(sortedBy("clientType", SortOrder.Direction.ASC))).containsExactly("application", "device", "integration");
    }

    @Test
    void nullsSortLastAscendingAndFirstDescendingAsInPostgreSql() {
        put(credentials("b", ClientType.DEVICE));
        put(credentials(null, ClientType.DEVICE));
        put(credentials("a", ClientType.DEVICE));

        assertThat(names(sortedBy("name", SortOrder.Direction.ASC))).containsExactly("a", "b", null);
        assertThat(names(sortedBy("name", SortOrder.Direction.DESC))).containsExactly(null, "b", "a");
    }

    @Test
    void latinOneAndOtherStringsSortTogether() {
        for (String name : List.of("клієнт", "b", "Ü", "a", "ab")) {
            put(credentials(name, ClientType.DEVICE));
        }

        assertThat(names(sortedBy("name", SortOrder.Direction.ASC))).containsExactly("a", "ab", "b", "Ü", "клієнт");
        assertThat(index.find(new PageLink(10, 0, "КЛІ"), null).totalElements()).isEqualTo(1);
        assertThat(index.find(new PageLink(10, 0, "ü"), null).totalElements()).isEqualTo(1);
    }

    @Test
    void stringsSortIgnoringCaseAndThenLowerCaseFirstAsPostgreSqlDoes() {
        // the order the postgres:17 image gives with its default collation, en_US.utf8
        for (String name : List.of("Banana", "AB", "apple", "aB", "Apple", "Ab", "banana", "ab", "test10", "Test9", "Test1")) {
            put(credentials(name, ClientType.DEVICE));
        }

        assertThat(names(sortedBy("name", SortOrder.Direction.ASC)))
                .containsExactly("ab", "aB", "Ab", "AB", "apple", "Apple", "banana", "Banana", "Test1", "test10", "Test9");
        assertThat(names(sortedBy("name", SortOrder.Direction.DESC)))
                .containsExactly("Test9", "test10", "Test1", "Banana", "banana", "Apple", "apple", "AB", "Ab", "aB", "ab");
    }

    @Test
    void latinOneAndOtherStringsIgnoreCaseAlike() {
        // kept as Latin-1 bytes and as strings, compared within and across the two forms
        for (String name : List.of("Клієнт", "Über", "uber", "клієнт", "über", "Uber")) {
            put(credentials(name, ClientType.DEVICE));
        }

        assertThat(names(sortedBy("name", SortOrder.Direction.ASC)))
                .containsExactly("uber", "Uber", "über", "Über", "клієнт", "Клієнт");
    }

    @Test
    void uuidsSortAsUnsignedBigEndianBytes() {
        UUID[] ids = {new UUID(-1L, 0), new UUID(0, -1L), new UUID(0, 1), new UUID(Long.MIN_VALUE, 0), new UUID(Long.MAX_VALUE, 0)};
        for (UUID id : ids) {
            MqttClientCredentials credentials = credentials(id.toString(), ClientType.DEVICE);
            credentials.setId(id);
            put(credentials);
        }
        List<Object> expected = List.of(new UUID(0, 1), new UUID(0, -1L), new UUID(Long.MAX_VALUE, 0), new UUID(Long.MIN_VALUE, 0), new UUID(-1L, 0));

        assertThat(index.find(new PageLink(10), null).ids()).containsExactlyElementsOf(expected);
        assertThat(index.find(new PageLink(10, 0, null, new SortOrder("id")), null).ids()).containsExactlyElementsOf(expected);
    }

    @Test
    void textSearchLowerCasesBothSidesWithTheRootLocale() {
        put(credentials("TITLE", ClientType.DEVICE));
        put(credentials("other", ClientType.DEVICE));

        assertThat(index.find(new PageLink(10, 0, "itl"), null).totalElements()).isEqualTo(1);
        assertThat(index.find(new PageLink(10, 0, "TiTlE"), null).totalElements()).isEqualTo(1);
        assertThat(index.find(new PageLink(10, 0, "titles"), null).totalElements()).isZero();
    }

    @Test
    void aTypeWithoutTextSearchIgnoresTheSearch() {
        ProjectionIndex<WebSocketSubscription> subscriptions = new ProjectionIndex<>(EntityDescriptors.WEBSOCKET_SUBSCRIPTION.type(), EntityDescriptors.WEBSOCKET_SUBSCRIPTION.projection());
        WebSocketSubscription subscription = new WebSocketSubscription();
        subscription.setId(UUID.randomUUID());
        subscriptions.put(subscriptions.project(subscription.getId(), subscription));

        assertThat(subscriptions.find(new PageLink(10, 0, "anything"), null).totalElements()).isEqualTo(1);
    }

    @Test
    void aPutReplacesTheRowOfItsIdAndARemoveDropsIt() {
        MqttClientCredentials credentials = credentials("before", ClientType.DEVICE);
        put(credentials);
        credentials.setName("after");
        put(credentials);

        assertThat(index.size()).isEqualTo(1);
        assertThat(names(sortedBy("name", SortOrder.Direction.ASC))).containsExactly("after");

        index.remove(credentials.getId());
        index.remove(UUID.randomUUID());

        assertThat(index.size()).isZero();
    }

    @Test
    void theSliceIsTakenAfterFilteringAndSorting() {
        for (int i = 0; i < 7; i++) {
            put(credentials("c" + i, i % 2 == 0 ? ClientType.DEVICE : ClientType.APPLICATION));
        }

        ProjectionIndex.Slice slice = index.find(new PageLink(2, 1, null, new SortOrder("name")),
                projected -> projected.filterValues().get("clientType") == ClientType.DEVICE);

        assertThat(slice.totalElements()).isEqualTo(4);
        assertThat(slice.ids()).hasSize(2);
        assertThat(index.find(new PageLink(2, 2, null, new SortOrder("name")), null).ids()).hasSize(2);
        assertThat(index.find(new PageLink(2, 3, null, new SortOrder("name")), null).ids()).hasSize(1);
        assertThat(index.find(new PageLink(2, 4, null, new SortOrder("name")), null).ids()).isEmpty();
        assertThat(index.find(new PageLink(2, Integer.MAX_VALUE, null, null), null).ids()).isEmpty();
    }

    @Test
    void aPageLinkThatSqlPagingRefusesIsRefused() {
        assertThatThrownBy(() -> index.find(new PageLink(0), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.find(new PageLink(10, -1), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.find(new PageLink(10, 0, null, new SortOrder("credentialsValue")), null))
                .isExactlyInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessage("Unknown sort property 'credentialsValue' of " + EntityDescriptors.MQTT_CLIENT_CREDENTIALS.type());
    }

    @Test
    void theProjectedMapsReadTheirOwnFieldsOnly() {
        MqttClientCredentials credentials = credentials("n", ClientType.DEVICE);
        credentials.setCredentialsValue("{\"certCnPattern\":\"CN-.*\",\"certCnIsRegex\":true}");
        put(credentials);
        List<RocksEntityStore.ProjectedEntity> seen = new ArrayList<>();

        index.find(new PageLink(10), projected -> seen.add(projected));

        assertThat(seen).singleElement().satisfies(projected -> {
            assertThat(projected.sortValues()).containsOnlyKeys("id", "createdTime", "credentialsId", "name", "clientType", "credentialsType");
            assertThat(projected.filterValues()).containsExactly(entry("clientType", ClientType.DEVICE),
                    entry("credentialsType", ClientCredentialsType.MQTT_BASIC),
                    entry("userName", null), entry("clientId", null), entry("certCnPattern", "CN-.*"));
            assertThat(projected.filterValues().get("name")).isNull();
            assertThat(projected.filterValues().containsKey("name")).isFalse();
            assertThat(projected.createdTime()).isEqualTo(42L);
        });
    }

    @Test
    void theProjectionExposesItsFieldsByName() {
        Projection<MqttClientCredentials> projection = EntityDescriptors.MQTT_CLIENT_CREDENTIALS.projection();
        MqttClientCredentials credentials = credentials("Name", ClientType.APPLICATION);
        credentials.setCredentialsValue("{\"userName\":\"u\",\"clientId\":\"c\"}");

        assertThat(projection.sortProperties()).containsOnlyKeys("id", "createdTime", "credentialsId", "name", "clientType", "credentialsType");
        assertThat(projection.filterFields()).containsOnlyKeys("clientType", "credentialsType", "userName", "clientId", "certCnPattern");
        assertThat(projection.textSearch().apply(credentials)).isEqualTo("Name");
        assertThat(projection.filterFields().get("userName").apply(credentials)).isEqualTo("u");
        assertThat(projection.sortProperties().get("clientType").apply(credentials)).isEqualTo(ClientType.APPLICATION);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "{not json", "[1, 2]", "\"text\"", "{\"userName\": 7, \"clientId\": {\"x\": 1}, \"certCnPattern\": null}"})
    void aCredentialsValueWithoutTheFieldsLeavesThemNull(String credentialsValue) {
        MqttClientCredentials credentials = credentials("n", ClientType.DEVICE);
        credentials.setCredentialsValue(credentialsValue);

        Map<String, Function<MqttClientCredentials, Object>> filters = EntityDescriptors.MQTT_CLIENT_CREDENTIALS.projection().filterFields();

        assertThat(filters.get("userName").apply(credentials)).isNull();
        assertThat(filters.get("clientId").apply(credentials)).isNull();
        assertThat(filters.get("certCnPattern").apply(credentials)).isNull();
    }

    // every type: buildProjections walks ALL, and a type missing there would silently start with an empty projection
    @Test
    void everyStoredTypeHasADescriptorAndEveryPagedTypeAProjection() {
        assertThat(EntityDescriptors.ALL).extracting(EntityDescriptor::type).containsExactly(StoredEntityType.values());
        assertThat(EntityDescriptors.ALL).filteredOn(d -> d.projection() != null).extracting(EntityDescriptor::type)
                .containsExactly(StoredEntityType.MQTT_AUTH_PROVIDER, StoredEntityType.MQTT_CLIENT_CREDENTIALS, StoredEntityType.USER,
                        StoredEntityType.WEBSOCKET_CONNECTION, StoredEntityType.WEBSOCKET_SUBSCRIPTION, StoredEntityType.UNAUTHORIZED_CLIENT);
    }

    @Test
    void aProjectionRejectsAFieldDeclaredTwice() {
        assertThatThrownBy(() -> Projection.<MqttClientCredentials>builder()
                .sort("name", MqttClientCredentials::getName)
                .filter("name", MqttClientCredentials::getName)
                .build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
    }

    private List<Object> sortedBy(String property, SortOrder.Direction direction) {
        return index.find(new PageLink(100, 0, null, new SortOrder(property, direction)), null).ids();
    }

    private List<String> names(List<Object> ids) {
        return ids.stream().map(namesById::get).toList();
    }

    private void put(MqttClientCredentials credentials) {
        namesById.put(credentials.getId(), credentials.getName());
        index.put(index.project(credentials.getId(), credentials));
    }

    private static MqttClientCredentials credentials(String name, ClientType clientType) {
        MqttClientCredentials credentials = new MqttClientCredentials();
        credentials.setId(UUID.randomUUID());
        credentials.setCreatedTime(42L);
        credentials.setCredentialsId(UUID.randomUUID().toString());
        credentials.setName(name);
        credentials.setClientType(clientType);
        credentials.setCredentialsType(ClientCredentialsType.MQTT_BASIC);
        return credentials;
    }

}
