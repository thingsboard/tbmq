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
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionChangesCallback;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SERVICE_ID_HEADER;
import static org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory.getClientSessionInfo;

class StandaloneClientSessionStateTest {

    // the service id of the test fixtures, so that a fixture session is "this node's" unless a test says otherwise
    private static final String CURRENT_SERVICE_ID = SERVICE_ID_HEADER;

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
        ServiceInfoProvider serviceInfoProvider = mock(ServiceInfoProvider.class);
        when(serviceInfoProvider.getServiceId()).thenReturn(CURRENT_SERVICE_ID);
        consumer = new StandaloneClientSessionConsumer(stores, serviceInfoProvider);
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
    void aStoreErrorWithoutACallbackIsSwallowedAndLeavesTheStoreUnchanged() {
        doThrow(new StoreException("put failed")).when(storeSpy).put(eq("c1"), any());

        assertThatCode(() -> persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1")), null))
                .doesNotThrowAnyException();

        assertThat(store.size()).isZero();
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
    void initLoadReturnsStoredSessionsAsDisconnectedWithTheDisconnectTimeSetOnlyWhereItWasMissing() throws Exception {
        long disconnectedAt = 12345L;
        persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1").toBuilder().connected(true).disconnectedAt(0).build()), null);
        persistence.persistClientSessionInfoAsync("c2", proto(persistentSession("c2").toBuilder().connected(false).disconnectedAt(disconnectedAt).build()), null);
        long before = System.currentTimeMillis();

        Map<String, ClientSessionInfo> loaded = consumer.initLoad();

        long after = System.currentTimeMillis();
        assertThat(loaded).containsOnlyKeys("c1", "c2");
        assertThat(loaded.get("c1").isConnected()).isFalse();
        assertThat(loaded.get("c1").getClientId()).isEqualTo("c1");
        assertThat(loaded.get("c1").getDisconnectedAt()).isBetween(before, after);
        assertThat(loaded.get("c2").isConnected()).isFalse();
        assertThat(loaded.get("c2").getDisconnectedAt()).isEqualTo(disconnectedAt);
    }

    @Test
    void allSessionsStoredConnectedGetTheSameLoadTime() throws Exception {
        persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1").toBuilder().connected(true).build()), null);
        persistence.persistClientSessionInfoAsync("c2", proto(persistentSession("c2").toBuilder().connected(true).build()), null);

        Map<String, ClientSessionInfo> loaded = consumer.initLoad();

        assertThat(loaded.get("c1").getDisconnectedAt()).isEqualTo(loaded.get("c2").getDisconnectedAt());
    }

    @Test
    void sessionExpiryIntervalStartsAtLoadForASessionStoredConnected_MQTT_3_1_2_23() throws Exception {
        ClientSessionInfo storedConnected = persistentSession("c1").toBuilder()
                .connected(true).disconnectedAt(0).cleanStart(true).sessionExpiryInterval(3600).build();
        persistence.persistClientSessionInfoAsync("c1", proto(storedConnected), null);

        ClientSessionInfo loaded = consumer.initLoad().get("c1");

        assertThat(loaded.isExpired(System.currentTimeMillis(), ClientSessionInfo.NO_TTL)).isFalse();
    }

    @Test
    void anMqtt5SessionWithCleanStartAndAnExpiryIntervalIsPersistentAndStored_MQTT_3_1_2_23() {
        ClientSessionInfo session = persistentSession("c1").toBuilder().cleanStart(true).sessionExpiryInterval(3600).build();

        persistence.persistClientSessionInfoAsync("c1", proto(session), callback);

        assertThat(store.get("c1")).isNotNull();
        verify(callback).onSuccess();
    }

    @Test
    void initLoadStoresBackASessionStoredConnectedAsDisconnectedAtTheLoadTime() throws Exception {
        store.put("c1", proto(persistentSession("c1").toBuilder().connected(true).disconnectedAt(0).build()).toByteArray());
        long before = System.currentTimeMillis();

        ClientSessionInfo loaded = consumer.initLoad().get("c1");

        long after = System.currentTimeMillis();
        ClientSessionInfo stored = decodeStored("c1");
        assertThat(stored.isConnected()).isFalse();
        assertThat(stored.getDisconnectedAt()).isBetween(before, after).isEqualTo(loaded.getDisconnectedAt());
    }

    @Test
    void theDisconnectTimeSurvivesAnotherRestart() throws Exception {
        store.put("c1", proto(persistentSession("c1").toBuilder().connected(true).disconnectedAt(0).build()).toByteArray());
        long firstLoadTs = consumer.initLoad().get("c1").getDisconnectedAt();
        Thread.sleep(5);

        long secondLoadTs = consumer.initLoad().get("c1").getDisconnectedAt();

        assertThat(secondLoadTs).isEqualTo(firstLoadTs);
    }

    @Test
    void aSessionStoredWithAnotherServiceIdLoadsAndIsStoredBackWithTheCurrentOne() throws Exception {
        ClientSessionInfo foreign = persistentSession("c1").toBuilder().connected(false).disconnectedAt(12345L).serviceId("old-node").build();
        store.put("c1", proto(foreign).toByteArray());

        ClientSessionInfo loaded = consumer.initLoad().get("c1");

        assertThat(loaded.getServiceId()).isEqualTo(CURRENT_SERVICE_ID);
        assertThat(loaded.getDisconnectedAt()).isEqualTo(12345L);
        assertThat(decodeStored("c1").getServiceId()).isEqualTo(CURRENT_SERVICE_ID);
        assertThat(decodeStored("c1").getDisconnectedAt()).isEqualTo(12345L);
    }

    @Test
    void aSessionStoredDisconnectedWithTheCurrentServiceIdIsNotRewritten() throws Exception {
        store.put("c1", proto(persistentSession("c1").toBuilder().connected(false).disconnectedAt(12345L).serviceId(CURRENT_SERVICE_ID).build()).toByteArray());

        consumer.initLoad();

        verify(storeSpy, never()).put(any(), any());
    }

    @Test
    void aFailureToStoreALoadedSessionBackFailsTheLoadAndNamesTheClient() {
        store.put("c1", proto(persistentSession("c1").toBuilder().connected(true).disconnectedAt(0).build()).toByteArray());
        StoreException failure = new StoreException("put failed");
        doThrow(failure).when(storeSpy).put(eq("c1"), any());

        assertThatThrownBy(() -> consumer.initLoad())
                .isInstanceOf(QueuePersistenceException.class)
                .hasMessageContaining("c1")
                .hasCause(failure);
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
        persistence.persistClientSessionInfoAsync("c1", proto(persistentSession("c1")), null);
        store.put("broken-client", new byte[]{(byte) 0xFF, 0x01, 0x02});

        assertThatThrownBy(() -> consumer.initLoad())
                .isInstanceOf(QueuePersistenceException.class)
                .hasMessageContaining("client sessions")
                .hasMessageContaining("broken-client");
    }

    @Test
    void listenBeforeOrAfterInitLoadDoesNothing() throws Exception {
        ClientSessionChangesCallback changes = mock(ClientSessionChangesCallback.class);

        consumer.listen(changes);
        consumer.initLoad();
        consumer.listen(changes);

        verifyNoInteractions(changes);
    }

    private ClientSessionInfo decodeStored(String clientId) throws Exception {
        return ProtoConverter.convertToClientSessionInfo(ClientSessionInfoProto.parseFrom(store.get(clientId)));
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
