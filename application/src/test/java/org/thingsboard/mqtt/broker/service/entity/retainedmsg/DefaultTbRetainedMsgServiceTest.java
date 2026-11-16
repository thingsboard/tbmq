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
package org.thingsboard.mqtt.broker.service.entity.retainedmsg;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.constants.QueueConstants;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerServiceImpl;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgPersistenceService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DefaultTbRetainedMsgServiceTest {

    private static final String TOPIC = "sensors/1";

    private final RetainedMsgService retainedMsgService = mock(RetainedMsgService.class);
    private final RetainedMsgPersistenceService persistenceService = mock(RetainedMsgPersistenceService.class);
    private final User admin = new User();

    private RetainedMsgListenerServiceImpl retainedMsgListenerService;
    private DefaultTbRetainedMsgService service;

    @BeforeEach
    void setUp() {
        retainedMsgListenerService = new RetainedMsgListenerServiceImpl(retainedMsgService, persistenceService,
                mock(ServiceInfoProvider.class), mock(StatsManager.class));
        retainedMsgListenerService.init(Map.of(TOPIC, retainedMsg()));
        service = new DefaultTbRetainedMsgService(retainedMsgService, retainedMsgListenerService);
    }

    @Test
    void givenAStoreThatFailsBeforeTheCallReturns_whenDelete_thenTheFailureReachesTheCallerAndTheRetainedMsgStays() {
        StoreException failure = new StoreException("disk");
        doAnswer(invocation -> {
            invocation.<BasicCallback>getArgument(2).onFailure(failure);
            return null;
        }).when(persistenceService).persistRetainedMsgAsync(eq(TOPIC), eq(QueueConstants.EMPTY_RETAINED_MSG_PROTO), any());

        assertThatThrownBy(() -> service.delete(TOPIC, admin)).isSameAs(failure);

        assertThat(retainedMsgListenerService.getRetainedMsgForTopic(TOPIC)).isNotNull();
        verify(retainedMsgService, never()).clearRetainedMsg(anyString());
    }

    @Test
    void givenAStoreThatClears_whenDelete_thenTheRetainedMsgIsGone() {
        doAnswer(invocation -> {
            invocation.<BasicCallback>getArgument(2).onSuccess();
            return null;
        }).when(persistenceService).persistRetainedMsgAsync(eq(TOPIC), eq(QueueConstants.EMPTY_RETAINED_MSG_PROTO), any());

        service.delete(TOPIC, admin);

        assertThat(retainedMsgListenerService.getRetainedMsgForTopic(TOPIC)).isNull();
        verify(retainedMsgService).clearRetainedMsg(TOPIC);
    }

    @Test
    void givenAProducerThatReportsLater_whenDelete_thenItReturnsAndAFailureReportedLaterIsOnlyLogged() {
        // the cluster producer: the call returns before the outcome is known, and a failure is never a StoreException
        assertThatCode(() -> service.delete(TOPIC, admin)).doesNotThrowAnyException();

        assertThat(retainedMsgListenerService.getRetainedMsgForTopic(TOPIC)).isNull();
        ArgumentCaptor<BasicCallback> persistCallback = ArgumentCaptor.forClass(BasicCallback.class);
        verify(persistenceService).persistRetainedMsgAsync(eq(TOPIC), eq(QueueConstants.EMPTY_RETAINED_MSG_PROTO), persistCallback.capture());
        assertThatCode(() -> persistCallback.getValue().onFailure(new RuntimeException("producer"))).doesNotThrowAnyException();
    }

    private static RetainedMsg retainedMsg() {
        return new RetainedMsg(TOPIC, "on".getBytes(StandardCharsets.UTF_8), 1, new MqttProperties());
    }

}
