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
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.consumer.BlockedClientChangesCallback;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.ClientIdBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexBlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.RegexMatchTarget;

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

class StandaloneBlockedClientStateTest {

    private InMemoryKeyValueStore store;
    private InMemoryKeyValueStore storeSpy;
    private BasicCallback callback;
    private StandaloneBlockedClientProducerService producer;
    private StandaloneBlockedClientConsumerService consumer;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        storeSpy = spy(store);
        BrokerStateStores stores = mock(BrokerStateStores.class);
        when(stores.blockedClients()).thenReturn(storeSpy);
        callback = mock(BasicCallback.class);
        producer = new StandaloneBlockedClientProducerService(stores);
        consumer = new StandaloneBlockedClientConsumerService(stores);
    }

    @Test
    void aStoredBlockedClientIsLoadedBackUnderItsKey() throws Exception {
        BlockedClient clientId = new ClientIdBlockedClient(1000L, "bad device", "c1");
        BlockedClient regex = new RegexBlockedClient(0L, null, "dev-.*", RegexMatchTarget.BY_CLIENT_ID);
        producer.persistBlockedClient(clientId.getKey(), proto(clientId), callback);
        producer.persistBlockedClient(regex.getKey(), proto(regex), null);

        Map<String, BlockedClient> loaded = consumer.initLoad();

        assertThat(loaded).containsOnlyKeys(clientId.getKey(), regex.getKey());
        BlockedClient loadedClientId = loaded.get(clientId.getKey());
        assertThat(loadedClientId).isEqualTo(clientId);
        assertThat(loadedClientId.getExpirationTime()).isEqualTo(1000L);
        assertThat(loadedClientId.getDescription()).isEqualTo("bad device");
        assertThat(loaded.get(regex.getKey())).isEqualTo(regex);
        assertThat(loaded.get(regex.getKey()).getRegexMatchTarget()).isEqualTo(RegexMatchTarget.BY_CLIENT_ID);
    }

    @Test
    void theBlockedClientIsStoredBeforeTheCallbackFires() {
        BlockedClient blocked = new ClientIdBlockedClient("c1");

        producer.persistBlockedClient(blocked.getKey(), proto(blocked), callback);

        InOrder o = inOrder(storeSpy, callback);
        o.verify(storeSpy).put(eq(blocked.getKey()), any());
        o.verify(callback).onSuccess();
    }

    @Test
    void theEmptyProtoDeletes() {
        BlockedClient blocked = new ClientIdBlockedClient("c1");
        producer.persistBlockedClient(blocked.getKey(), proto(blocked), null);
        assertThat(store.get(blocked.getKey())).isNotNull();

        producer.persistBlockedClient(blocked.getKey(), QueueConstants.EMPTY_BLOCKED_CLIENT_PROTO, callback);

        assertThat(store.get(blocked.getKey())).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void aStoreErrorReachesTheCallback() {
        doThrow(new StoreException("put failed")).when(storeSpy).put(any(), any());

        producer.persistBlockedClient("k", proto(new ClientIdBlockedClient("c1")), callback);

        verify(callback).onFailure(any(StoreException.class));
        verify(callback, never()).onSuccess();
    }

    @Test
    void aStoreErrorWithoutACallbackIsThrown() {
        doThrow(new StoreException("put failed")).when(storeSpy).put(any(), any());

        assertThatThrownBy(() -> producer.persistBlockedClient("k", proto(new ClientIdBlockedClient("c1")), null))
                .isInstanceOf(StoreException.class);
    }

    @Test
    void theDummyVariantDoesNothing() throws Exception {
        producer.persistDummyBlockedClient("k", proto(new ClientIdBlockedClient("c1")));

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
        BlockedClient blocked = new ClientIdBlockedClient("c1");
        producer.persistBlockedClient(blocked.getKey(), proto(blocked), null);
        store.put("broken-key", new byte[]{(byte) 0xFF, 0x01, 0x02});

        assertThatThrownBy(() -> consumer.initLoad())
                .isInstanceOf(QueuePersistenceException.class)
                .hasMessageContaining("blocked clients")
                .hasMessageContaining("broken-key");
    }

    @Test
    void listenDoesNothing() throws Exception {
        BlockedClientChangesCallback changes = mock(BlockedClientChangesCallback.class);

        consumer.listen(changes);
        consumer.initLoad();
        consumer.listen(changes);

        verifyNoInteractions(changes);
    }

    private static BlockedClientProto proto(BlockedClient blockedClient) {
        return ProtoConverter.convertToBlockedClientProto(blockedClient);
    }

}
