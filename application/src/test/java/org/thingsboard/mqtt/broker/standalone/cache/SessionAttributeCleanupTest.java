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
package org.thingsboard.mqtt.broker.standalone.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.thingsboard.mqtt.broker.cache.CacheConstants;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.cache.CacheSpecs;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.standalone.state.InMemoryKeyValueStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionAttributeCleanupTest {

    private final InMemoryKeyValueStore store = new InMemoryKeyValueStore();
    private final ClientSessionCache sessionCache = mock(ClientSessionCache.class);
    private final SessionAttributeCleanup cleanup = new SessionAttributeCleanup(stores(store), sessionCache, cacheProperties());

    @Test
    void attributesOfClientsWithoutASessionAreRemoved() {
        when(sessionCache.getAllClientSessions()).thenReturn(Map.of("c1", mock(ClientSessionInfo.class)));
        new StoreBackedCache("p:clientSessionCredentials", store).put("c1", "creds-1");
        new StoreBackedCache("p:clientSessionCredentials", store).put("c2", "creds-2");
        new StoreBackedCache("p:clientMqttVersion", store).put("c1", "5");
        new StoreBackedCache("p:clientMqttVersion", store).put("c2", "3");

        cleanup.removeAttributesOfClientsWithoutSession();

        assertThat(store.snapshot()).containsOnlyKeys(
                "p:clientSessionCredentials\u0000c1",
                "p:clientMqttVersion\u0000c1");
    }

    @Test
    void aKeyUnderAnotherCacheNameIsRemovedEvenForAClientWithASession() {
        when(sessionCache.getAllClientSessions()).thenReturn(Map.of("c1", mock(ClientSessionInfo.class)));
        new StoreBackedCache("old:clientSessionCredentials", store).put("c1", "creds-1");
        new StoreBackedCache("old:clientMqttVersion", store).put("c1", "5");
        new StoreBackedCache("p:clientSessionCredentials", store).put("c1", "creds-1");
        new StoreBackedCache("p:clientMqttVersion", store).put("c1", "5");

        cleanup.removeAttributesOfClientsWithoutSession();

        assertThat(store.snapshot()).containsOnlyKeys(
                "p:clientSessionCredentials\u0000c1",
                "p:clientMqttVersion\u0000c1");
    }

    @Test
    void aKeyWithoutTheSeparatorIsRemoved() {
        when(sessionCache.getAllClientSessions()).thenReturn(Map.of("c1", mock(ClientSessionInfo.class)));
        store.put("garbage", new byte[]{1});

        cleanup.removeAttributesOfClientsWithoutSession();

        assertThat(store.size()).isZero();
    }

    @Test
    void aClientIdThatContainsOtherCharactersIsMatchedAfterTheFirstSeparator() {
        when(sessionCache.getAllClientSessions()).thenReturn(Map.of("a:b", mock(ClientSessionInfo.class)));
        new StoreBackedCache("p:clientMqttVersion", store).put("a:b", "5");

        cleanup.removeAttributesOfClientsWithoutSession();

        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void theAttributesOfEveryStoreBackedCacheOfTheManagerAreKeptForAClientWithASession() {
        when(sessionCache.getAllClientSessions()).thenReturn(Map.of("c1", mock(ClientSessionInfo.class)));
        CacheProperties properties = cacheProperties();
        properties.setSpecs(Map.of(
                CacheConstants.MQTT_CLIENT_CREDENTIALS_CACHE, new CacheSpecs(),
                CacheConstants.BASIC_CREDENTIALS_PASSWORD_CACHE, new CacheSpecs(),
                CacheConstants.SSL_REGEX_BASED_CREDENTIALS_CACHE, new CacheSpecs(),
                CacheConstants.CLIENT_SESSION_CREDENTIALS_CACHE, new CacheSpecs(),
                CacheConstants.CLIENT_MQTT_VERSION_CACHE, new CacheSpecs()));
        StandaloneCacheManager manager = StandaloneCacheManager.create(properties, store, 10, Ticker.systemTicker());
        List<Cache> storeBacked = manager.getCacheNames().stream()
                .map(manager::getCache)
                .filter(StoreBackedCache.class::isInstance)
                .toList();
        storeBacked.forEach(cache -> cache.put("c1", "value"));

        new SessionAttributeCleanup(stores(store), sessionCache, properties).removeAttributesOfClientsWithoutSession();

        assertThat(storeBacked).isNotEmpty();
        assertThat(store.size()).isEqualTo(storeBacked.size());
    }

    @Test
    void anEmptyStoreStaysEmpty() {
        when(sessionCache.getAllClientSessions()).thenReturn(Map.of());

        cleanup.removeAttributesOfClientsWithoutSession();

        assertThat(store.size()).isZero();
    }

    private static CacheProperties cacheProperties() {
        CacheProperties properties = new CacheProperties();
        properties.setCachePrefix("p:");
        return properties;
    }

    private static BrokerStateStores stores(InMemoryKeyValueStore attributes) {
        BrokerStateStores stores = mock(BrokerStateStores.class);
        when(stores.clientSessionAttributes()).thenReturn(attributes);
        return stores;
    }

}
