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
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionInfoProto;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionChangesCallback;

import java.util.Map;

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

class StandaloneClientSessionStateTest {

    private InMemoryKeyValueStore store;
    private InMemoryKeyValueStore storeSpy;
    private BasicCallback callback;
    private StandaloneClientSessionPersistenceService persistence;
    private StandaloneClientSessionConsumer consumer;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        storeSpy = spy(store);
        BrokerStateStores stores = mock(BrokerStateStores.class);
        when(stores.clientSessions()).thenReturn(storeSpy);
        callback = mock(BasicCallback.class);
        persistence = new StandaloneClientSessionPersistenceService(stores);
        consumer = new StandaloneClientSessionConsumer(stores);
    }

    @Test
    void aPersistentSessionIsStoredBeforeTheCallbackFires() {
        persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1")), callback);

        InOrder o = inOrder(storeSpy, callback);
        o.verify(storeSpy).put(eq("c1"), any());
        o.verify(callback).onSuccess();
        assertThat(store.get("c1")).isEqualTo(proto(persistentSession("c1")).toByteArray());
    }

    @Test
    void aNonPersistentSessionIsNotStoredAndRemovesAnOlderEntry() {
        persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1")), null);
        assertThat(store.get("c1")).isNotNull();

        persistence.persistClientSessionInfoAsync("c1", proto(cleanSession("c1")), callback);

        assertThat(store.get("c1")).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void theEmptyProtoDeletes() {
        persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1")), null);
        assertThat(store.get("c1")).isNotNull();

        persistence.persistClientSessionInfoAsync("c1", QueueConstants.EMPTY_CLIENT_SESSION_INFO_PROTO, callback);

        assertThat(store.get("c1")).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void aStoreErrorReachesTheCallback() {
        StoreException failure = new StoreException("put failed");
        doThrow(failure).when(storeSpy).put(eq("c1"), any());

        persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1")), callback);

        verify(callback).onFailure(any(StoreException.class));
        verify(callback, never()).onSuccess();
    }

    @Test
    void aStoreErrorWithoutACallbackIsThrown() {
        doThrow(new StoreException("put failed")).when(storeSpy).put(eq("c1"), any());

        assertThatThrownBy(() -> persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1")), null))
                .isInstanceOf(StoreException.class);
    }

    @Test
    void theSyncVariantStoresAndDeletesLikeTheAsyncOne() throws Exception {
        persistence.persistClientSessionInfoSync("c1", proto(persistentSession("c1")));
        assertThat(store.get("c1")).isNotNull();

        persistence.persistClientSessionInfoSync("c1", proto(cleanSession("c1")));
        assertThat(store.get("c1")).isNull();

        persistence.persistClientSessionInfoSync("c2", proto(persistentSession("c2")));
        persistence.persistClientSessionInfoSync("c2", QueueConstants.EMPTY_CLIENT_SESSION_INFO_PROTO);
        assertThat(store.get("c2")).isNull();
    }

    @Test
    void theSyncVariantWrapsAStoreErrorInQueuePersistenceException() {
        StoreException failure = new StoreException("put failed");
        doThrow(failure).when(storeSpy).put(eq("c1"), any());

        assertThatThrownBy(() -> persistence.persistClientSessionInfoSync("c1", proto(persistentSession("c1"))))
                .isInstanceOf(QueuePersistenceException.class)
                .hasCause(failure);
    }

    @Test
    void initLoadReturnsStoredSessionsAsDisconnected() throws Exception {
        persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1").toBuilder().connected(true).build()), null);

        Map<String, ClientSessionInfo> loaded = consumer.initLoad();

        assertThat(loaded).containsOnlyKeys("c1").allSatisfy((id, s) -> {
            assertThat(s.isConnected()).isFalse();
            assertThat(s.getClientId()).isEqualTo("c1");
        });
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
    void listenBeforeOrAfterInitLoadDoesNothing() throws Exception {
        ClientSessionChangesCallback changes = mock(ClientSessionChangesCallback.class);

        consumer.listen(changes);
        consumer.initLoad();
        consumer.listen(changes);

        verifyNoInteractions(changes);
    }

    private static ClientSessionInfo persistentSession(String clientId) {
        return getClientSessionInfo(false, clientId, ClientType.DEVICE, false);
    }

    private static ClientSessionInfo cleanSession(String clientId) {
        return getClientSessionInfo(true, clientId, ClientType.DEVICE, true);
    }

    private static ClientSessionInfoProto proto(ClientSessionInfo info) {
        return ProtoConverter.convertToClientSessionInfoProto(info);
    }

}
