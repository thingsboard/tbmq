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
package org.thingsboard.mqtt.broker.standalone.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.IntegrationTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionChangesCallback;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSource;
import org.thingsboard.mqtt.broker.service.subscription.data.SubscriptionsSourceKey;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getClientSessionInfo;

class StandaloneSubscriptionStateTest {

    private InMemoryKeyValueStore store;
    private InMemoryKeyValueStore storeSpy;
    private ClientSessionCache clientSessionCache;
    private BasicCallback callback;
    private StandaloneSubscriptionPersistenceService persistence;
    private StandaloneClientSubscriptionConsumer consumer;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        storeSpy = spy(store);
        BrokerStateStores stores = mock(BrokerStateStores.class);
        when(stores.clientSubscriptions()).thenReturn(storeSpy);
        clientSessionCache = mock(ClientSessionCache.class);
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(persistentSession("c1"));
        callback = mock(BasicCallback.class);
        persistence = new StandaloneSubscriptionPersistenceService(stores, clientSessionCache);
        consumer = new StandaloneClientSubscriptionConsumer(stores);
    }

    @Test
    void storedSubscriptionsAreLoadedBack() throws Exception {
        TopicSubscription shared = new ClientTopicSubscription("x/+", 2, "grp");
        persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1), shared), callback);

        Map<SubscriptionsSourceKey, Set<TopicSubscription>> loaded = consumer.initLoad();

        assertThat(loaded).containsOnlyKeys(SubscriptionsSourceKey.newInstance("c1"));
        Set<TopicSubscription> subs = loaded.get(SubscriptionsSourceKey.newInstance("c1"));
        assertThat(subs).hasSize(2);
        assertThat(subs).filteredOn(s -> s.getTopicFilter().equals("a/b"))
                .singleElement().satisfies(s -> {
                    assertThat(s.getQos()).isEqualTo(1);
                    assertThat(s.getShareName()).isNull();
                });
        assertThat(subs).filteredOn(s -> s.getTopicFilter().equals("x/+"))
                .singleElement().satisfies(s -> {
                    assertThat(s.getQos()).isEqualTo(2);
                    assertThat(s.getShareName()).isEqualTo("grp");
                });
    }

    @Test
    void theSourceOfTheSubscriptionsIsKeptInTheLoadedKey() throws Exception {
        when(clientSessionCache.getClientSessionInfo("int1")).thenReturn(persistentSession("int1"));
        persistence.persistClientSubscriptionsAsync("int1", Set.of(new IntegrationTopicSubscription("i/#")), null);

        Map<SubscriptionsSourceKey, Set<TopicSubscription>> loaded = consumer.initLoad();

        assertThat(loaded).hasSize(1);
        SubscriptionsSourceKey key = loaded.keySet().iterator().next();
        assertThat(key.getId()).isEqualTo("int1");
        assertThat(key.getSource()).isEqualTo(SubscriptionsSource.INTEGRATION);
        assertThat(loaded.get(key)).containsExactly(new IntegrationTopicSubscription("i/#"));
    }

    @Test
    void theSubscriptionsAreStoredBeforeTheCallbackFires() {
        persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1)), callback);

        InOrder o = inOrder(storeSpy, callback);
        o.verify(storeSpy).put(eq("c1"), any());
        o.verify(callback).onSuccess();
    }

    @Test
    void anEmptySetDeletesTheKey() {
        persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1)), null);
        assertThat(store.get("c1")).isNotNull();

        persistence.persistClientSubscriptionsAsync("c1", Set.of(), callback);

        assertThat(store.get("c1")).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void subscriptionsOfANonPersistentSessionAreNotStored() {
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(cleanSession("c1"));

        persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1)), callback);

        assertThat(store.get("c1")).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void subscriptionsOfAnUnknownSessionAreNotStored() {
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(null);

        persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1)), callback);

        assertThat(store.get("c1")).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void anOlderEntryOfASessionThatIsNoLongerPersistentIsDeleted() {
        persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1)), null);
        assertThat(store.get("c1")).isNotNull();
        when(clientSessionCache.getClientSessionInfo("c1")).thenReturn(cleanSession("c1"));

        persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1)), callback);

        assertThat(store.get("c1")).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void aStoreErrorReachesTheCallback() {
        doThrow(new StoreException("put failed")).when(storeSpy).put(eq("c1"), any());

        persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1)), callback);

        verify(callback).onFailure(any(StoreException.class));
        verify(callback, never()).onSuccess();
    }

    @Test
    void aStoreErrorWithoutACallbackIsThrown() {
        doThrow(new StoreException("put failed")).when(storeSpy).put(eq("c1"), any());

        assertThatThrownBy(() -> persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1)), null))
                .isInstanceOf(StoreException.class);
    }

    @Test
    void theSyncVariantDoesNothing() throws Exception {
        persistence.persistClientSubscriptionsSync("c1", Set.of(sub("a/b", 1)));

        assertThat(store.size()).isZero();
        verifyNoInteractions(storeSpy);
    }

    @Test
    void initLoadOfAnEmptyStoreReturnsNothing() throws Exception {
        assertThat(consumer.initLoad()).isEmpty();
    }

    @Test
    void initLoadWrapsAStoreErrorInQueuePersistenceException() {
        StoreException failure = new StoreException("scan failed");
        doThrow(failure).when(storeSpy).forEach(any());

        assertThatThrownBy(() -> consumer.initLoad())
                .isInstanceOf(QueuePersistenceException.class)
                .hasCause(failure);
    }

    @Test
    void initLoadFailsOnAnUnreadableEntryAndNamesTheStoreAndTheKey() {
        persistence.persistClientSubscriptionsAsync("c1", Set.of(sub("a/b", 1)), null);
        store.put("broken-client", new byte[]{(byte) 0xFF, 0x01, 0x02});

        assertThatThrownBy(() -> consumer.initLoad())
                .isInstanceOf(QueuePersistenceException.class)
                .hasMessageContaining("client subscriptions")
                .hasMessageContaining("broken-client");
    }

    @Test
    void listenDoesNothing() throws Exception {
        ClientSubscriptionChangesCallback changes = mock(ClientSubscriptionChangesCallback.class);

        consumer.listen(changes);
        consumer.initLoad();
        consumer.listen(changes);

        verifyNoInteractions(changes);
    }

    private static TopicSubscription sub(String filter, int qos) {
        return new ClientTopicSubscription(filter, qos);
    }

    private static ClientSessionInfo persistentSession(String clientId) {
        return getClientSessionInfo(false, clientId, ClientType.DEVICE, false);
    }

    private static ClientSessionInfo cleanSession(String clientId) {
        return getClientSessionInfo(true, clientId, ClientType.DEVICE, true);
    }

}
