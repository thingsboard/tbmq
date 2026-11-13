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
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.RetainedMsgProto;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgChangesCallback;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

class StandaloneRetainedMsgStateTest {

    private InMemoryKeyValueStore store;
    private InMemoryKeyValueStore storeSpy;
    private BasicCallback callback;
    private StandaloneRetainedMsgPersistenceService persistence;
    private StandaloneRetainedMsgConsumer consumer;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        storeSpy = spy(store);
        BrokerStateStores stores = mock(BrokerStateStores.class);
        when(stores.retainedMessages()).thenReturn(storeSpy);
        callback = mock(BasicCallback.class);
        persistence = new StandaloneRetainedMsgPersistenceService(stores);
        consumer = new StandaloneRetainedMsgConsumer(stores);
    }

    @Test
    void aStoredRetainedMessageIsLoadedBack() throws Exception {
        RetainedMsg original = retained("t/1", 1);
        persistence.persistRetainedMsgAsync("t/1", proto(original), callback);

        Map<String, RetainedMsg> loaded = consumer.initLoad();

        assertThat(loaded).containsOnlyKeys("t/1");
        RetainedMsg msg = loaded.get("t/1");
        assertThat(msg.getTopic()).isEqualTo("t/1");
        assertThat(msg.getPayload()).isEqualTo(original.getPayload());
        assertThat(msg.getQos()).isEqualTo(1);
        assertThat(msg.getCreatedTime()).isEqualTo(original.getCreatedTime());
    }

    @Test
    void theRetainedMessageIsStoredBeforeTheCallbackFires() {
        persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), callback);

        InOrder o = inOrder(storeSpy, callback);
        o.verify(storeSpy).put(eq("t"), any());
        o.verify(callback).onSuccess();
    }

    @Test
    void theEmptyProtoDeletes() {
        persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), null);
        assertThat(store.get("t")).isNotNull();

        persistence.persistRetainedMsgAsync("t", QueueConstants.EMPTY_RETAINED_MSG_PROTO, callback);

        assertThat(store.get("t")).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void theEmptyProtoDeletesAlsoThroughTheOverloadWithAnInterval() {
        persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), null);

        persistence.persistRetainedMsgAsync("t", QueueConstants.EMPTY_RETAINED_MSG_PROTO, callback, 30);

        assertThat(store.get("t")).isNull();
        verify(callback).onSuccess();
    }

    @Test
    void theRetainedMessageExpiryIntervalSurvivesAReload() throws Exception {
        persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), null, 30);

        assertThat(consumer.initLoad().get("t").getProperties().getProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID).value())
                .isEqualTo(30);
    }

    @Test
    void aRetainedMessageWithoutAnIntervalLoadsWithoutOne() throws Exception {
        persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), null);

        assertThat(consumer.initLoad().get("t").getProperties().getProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID))
                .isNull();
    }

    @Test
    void aStoreErrorReachesTheCallback() {
        doThrow(new StoreException("put failed")).when(storeSpy).put(eq("t"), any());

        persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), callback);

        verify(callback).onFailure(any(StoreException.class));
        verify(callback, never()).onSuccess();
    }

    @Test
    void aStoreErrorWithTheIntervalReachesTheCallback() {
        doThrow(new StoreException("put failed")).when(storeSpy).put(eq("t"), any());

        persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), callback, 30);

        verify(callback).onFailure(any(StoreException.class));
        verify(callback, never()).onSuccess();
    }

    @Test
    void aStoreErrorWithoutACallbackIsSwallowedAndLeavesTheStoreUnchanged() {
        doThrow(new StoreException("put failed")).when(storeSpy).put(eq("t"), any());

        assertThatCode(() -> persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), null))
                .doesNotThrowAnyException();

        assertThat(store.size()).isZero();
    }

    @Test
    void theSyncVariantDoesNothing() throws Exception {
        persistence.persistRetainedMsgSync("t", proto(retained("t", 0)));

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
        persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), null);
        store.put("broken/topic", new byte[]{(byte) 0xFF, 0x01, 0x02});

        assertThatThrownBy(() -> consumer.initLoad())
                .isInstanceOf(QueuePersistenceException.class)
                .hasMessageContaining("retained messages")
                .hasMessageContaining("broken/topic");
    }

    @Test
    void initLoadFailsOnAnEntryWithAnUnknownEnvelopeVersion() {
        byte[] value = RetainedMsgEnvelope.encode(null, proto(retained("t", 0)));
        value[0] = 99;
        store.put("topic/x", value);

        assertThatThrownBy(() -> consumer.initLoad())
                .isInstanceOf(QueuePersistenceException.class)
                .hasMessageContaining("retained messages")
                .hasMessageContaining("topic/x")
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listenDoesNothing() throws Exception {
        RetainedMsgChangesCallback changes = mock(RetainedMsgChangesCallback.class);

        consumer.listen(changes);
        consumer.initLoad();
        consumer.listen(changes);

        verifyNoInteractions(changes);
    }

    @Test
    void theEnvelopeRoundTripsTheIntervalAndTheProto() throws Exception {
        RetainedMsgProto proto = proto(retained("t", 2));

        RetainedMsgEnvelope.Decoded decoded = RetainedMsgEnvelope.decode(RetainedMsgEnvelope.encode(60, proto));

        assertThat(decoded.messageExpiryInterval()).isEqualTo(60);
        assertThat(decoded.proto()).isEqualTo(proto);
    }

    @Test
    void theEnvelopeRoundTripsAZeroInterval() throws Exception {
        RetainedMsgEnvelope.Decoded decoded = RetainedMsgEnvelope.decode(RetainedMsgEnvelope.encode(0, proto(retained("t", 0))));

        assertThat(decoded.messageExpiryInterval()).isZero();
    }

    @Test
    void theEnvelopeRoundTripsAMissingInterval() throws Exception {
        RetainedMsgProto proto = proto(retained("t", 1));

        RetainedMsgEnvelope.Decoded decoded = RetainedMsgEnvelope.decode(RetainedMsgEnvelope.encode(null, proto));

        assertThat(decoded.messageExpiryInterval()).isNull();
        assertThat(decoded.proto()).isEqualTo(proto);
    }

    @Test
    void theEnvelopeLayoutIsVersionFlagsAFourByteIntervalThenTheProto() {
        RetainedMsgProto proto = proto(retained("t", 1));

        byte[] withInterval = RetainedMsgEnvelope.encode(0x01020304, proto);
        byte[] withoutInterval = RetainedMsgEnvelope.encode(null, proto);

        assertThat(withInterval).startsWith(1, 1, 1, 2, 3, 4);
        assertThat(withoutInterval).startsWith(1, 0, 0, 0, 0, 0);
        assertThat(withInterval).hasSize(6 + proto.getSerializedSize());
        assertThat(withoutInterval).hasSize(6 + proto.getSerializedSize());
        assertThat(Arrays.copyOfRange(withInterval, 6, withInterval.length)).isEqualTo(proto.toByteArray());
        assertThat(Arrays.copyOfRange(withoutInterval, 6, withoutInterval.length)).isEqualTo(proto.toByteArray());
    }

    @Test
    void theEnvelopeRoundTripsAnIntervalOfMinusOne_0xFFFFFFFF() throws Exception {
        RetainedMsgProto proto = proto(retained("t", 1));

        RetainedMsgEnvelope.Decoded decoded = RetainedMsgEnvelope.decode(RetainedMsgEnvelope.encode(-1, proto));

        assertThat(decoded.messageExpiryInterval()).isEqualTo(-1);
        assertThat(decoded.proto()).isEqualTo(proto);
    }

    @Test
    void theEnvelopeRejectsAnUnknownFlagBit() {
        byte[] value = RetainedMsgEnvelope.encode(30, proto(retained("t", 0)));
        value[1] = 0b11;

        assertThatThrownBy(() -> RetainedMsgEnvelope.decode(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flags");
    }

    @Test
    void anIntervalOfMinusOneSurvivesPersistAndInitLoad() throws Exception {
        persistence.persistRetainedMsgAsync("t", proto(retained("t", 0)), null, -1);

        assertThat(consumer.initLoad().get("t").getProperties().getProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID).value())
                .isEqualTo(-1);
    }

    @Test
    void theEnvelopeRejectsAnUnknownVersion() {
        byte[] value = RetainedMsgEnvelope.encode(null, proto(retained("t", 0)));
        value[0] = 2;

        assertThatThrownBy(() -> RetainedMsgEnvelope.decode(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version")
                .hasMessageContaining("2");
    }

    @Test
    void theEnvelopeRejectsATooShortValue() {
        assertThatThrownBy(() -> RetainedMsgEnvelope.decode(new byte[]{1, 0, 0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
        assertThatThrownBy(() -> RetainedMsgEnvelope.decode(new byte[]{1, 1, 0, 0, 0}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
        assertThatThrownBy(() -> RetainedMsgEnvelope.decode(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    private static RetainedMsg retained(String topic, int qos) {
        return new RetainedMsg(topic, "payload".getBytes(StandardCharsets.UTF_8), qos);
    }

    private static RetainedMsgProto proto(RetainedMsg msg) {
        return ProtoConverter.convertToRetainedMsgProto(msg);
    }

}
