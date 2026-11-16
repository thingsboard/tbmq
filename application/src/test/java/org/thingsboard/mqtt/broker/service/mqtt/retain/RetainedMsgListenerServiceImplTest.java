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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RetainedMsgListenerServiceImplTest {

    private static final String TOPIC = "sensors/1";

    private final RetainedMsgService retainedMsgService = mock(RetainedMsgService.class);
    private final RetainedMsgPersistenceService persistenceService = mock(RetainedMsgPersistenceService.class);
    private final BasicCallback callback = mock(BasicCallback.class);

    private RetainedMsgListenerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RetainedMsgListenerServiceImpl(retainedMsgService, persistenceService, mock(ServiceInfoProvider.class), mock(StatsManager.class));
        service.init(Map.of());
    }

    @Test
    void givenAStoreThatFailsBeforeTheCallReturns_whenCacheAndPersist_thenTheCacheIsUnchanged() {
        storeFails();

        service.cacheRetainedMsgAndPersist(TOPIC, retainedMsg("new"), callback);

        // no subscriber may get a retained msg that was not stored
        assertThat(service.getRetainedMsgForTopic(TOPIC)).isNull();
        verify(retainedMsgService, never()).saveRetainedMsg(anyString(), any());
        verify(callback).onFailure(any(StoreException.class));
    }

    @Test
    void givenAStoreThatWrites_whenCacheAndPersist_thenTheMsgIsCachedAfterTheWrite() {
        storeWrites();

        service.cacheRetainedMsgAndPersist(TOPIC, retainedMsg("new"), callback);

        assertThat(service.getRetainedMsgForTopic(TOPIC)).isNotNull();
        verify(retainedMsgService).saveRetainedMsg(anyString(), any());
        verify(callback).onSuccess();
    }

    @Test
    void givenAStoreThatFailsBeforeTheCallReturns_whenClearAndPersist_thenTheCachedMsgStays() {
        storeWrites();
        service.cacheRetainedMsgAndPersist(TOPIC, retainedMsg("old"), mock(BasicCallback.class));
        storeFails();

        service.clearRetainedMsgAndPersist(TOPIC, callback);

        assertThat(service.getRetainedMsgForTopic(TOPIC)).isNotNull();
        verify(retainedMsgService, never()).clearRetainedMsg(anyString());
        verify(callback).onFailure(any(StoreException.class));
    }

    @Test
    void givenAProducerThatReportsLater_whenCacheAndPersist_thenTheMsgIsCachedAtOnce() {
        // the cluster producer: the call returns before the outcome is known

        service.cacheRetainedMsgAndPersist(TOPIC, retainedMsg("new"), callback);

        assertThat(service.getRetainedMsgForTopic(TOPIC)).isNotNull();
    }

    @Test
    void givenAStoreFailureReportedAfterTheCallReturned_whenCacheAndPersist_thenTheMsgStaysCached() {
        // only a failure reported before the persist call returns keeps the cache as it was (RetainedMsgPersistenceService)
        service.cacheRetainedMsgAndPersist(TOPIC, retainedMsg("new"), callback);
        ArgumentCaptor<BasicCallback> persistCallback = ArgumentCaptor.forClass(BasicCallback.class);
        verify(persistenceService).persistRetainedMsgAsync(eq(TOPIC), any(), persistCallback.capture());

        persistCallback.getValue().onFailure(new StoreException("disk"));

        assertThat(service.getRetainedMsgForTopic(TOPIC)).isNotNull();
        verify(retainedMsgService).saveRetainedMsg(eq(TOPIC), any());
        verify(callback).onFailure(any(StoreException.class));
    }

    private void storeFails() {
        doAnswer(invocation -> {
            invocation.<BasicCallback>getArgument(2).onFailure(new StoreException("disk"));
            return null;
        }).when(persistenceService).persistRetainedMsgAsync(anyString(), any(), any());
    }

    private void storeWrites() {
        doAnswer(invocation -> {
            invocation.<BasicCallback>getArgument(2).onSuccess();
            return null;
        }).when(persistenceService).persistRetainedMsgAsync(anyString(), any(), any());
    }

    private static RetainedMsg retainedMsg(String payload) {
        return new RetainedMsg(TOPIC, payload.getBytes(StandardCharsets.UTF_8), 1, new MqttProperties());
    }

}
