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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.NoOpApplicationRemovedEventService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic.NoOpApplicationTopicService;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ApplicationStandInsTest {

    private static final String MSG = "APPLICATION clients are not supported in TBMQ Lite";

    @Test
    void givenTopicService_whenCreateTopic_thenThrowsUnsupported() {
        assertThatThrownBy(() -> new NoOpApplicationTopicService().createTopic("c"))
                .isInstanceOf(UnsupportedOperationException.class).hasMessage(MSG);
    }

    @Test
    void givenTopicService_whenCreateSharedTopic_thenThrowsUnsupported() {
        ApplicationSharedSubscription subscription = mock(ApplicationSharedSubscription.class);
        assertThatThrownBy(() -> new NoOpApplicationTopicService().createSharedTopic(subscription))
                .isInstanceOf(UnsupportedOperationException.class).hasMessage(MSG);
    }

    @Test
    void givenTopicService_whenDeleteTopic_thenCallbackSucceeds() {
        BasicCallback basic = mock(BasicCallback.class);

        new NoOpApplicationTopicService().deleteTopic("c", basic);

        verify(basic).onSuccess();
        verify(basic, never()).onFailure(any());
    }

    @Test
    void givenTopicService_whenDeleteSharedTopic_thenNoOp() {
        ApplicationSharedSubscription subscription = mock(ApplicationSharedSubscription.class);

        assertThatCode(() -> new NoOpApplicationTopicService().deleteSharedTopic(subscription)).doesNotThrowAnyException();
        verifyNoInteractions(subscription);
    }

    @Test
    void givenQueuePublisher_whenSendMsg_thenCallbackFails() {
        PublishMsgCallback publish = mock(PublishMsgCallback.class);
        @SuppressWarnings("unchecked")
        TbProtoQueueMsg<PublishMsgProto> queueMsg = mock(TbProtoQueueMsg.class);

        new NoOpApplicationMsgQueuePublisher().sendMsg("c", queueMsg, publish);

        verify(publish).onFailure(any(UnsupportedOperationException.class));
        verify(publish, never()).onSuccess();
    }

    @Test
    void givenQueuePublisher_whenSendMsgToSharedTopic_thenCallbackFails() {
        PublishMsgCallback publish = mock(PublishMsgCallback.class);
        @SuppressWarnings("unchecked")
        TbProtoQueueMsg<PublishMsgProto> queueMsg = mock(TbProtoQueueMsg.class);

        new NoOpApplicationMsgQueuePublisher().sendMsgToSharedTopic("t", queueMsg, publish);

        verify(publish).onFailure(any(UnsupportedOperationException.class));
        verify(publish, never()).onSuccess();
    }

    @Test
    void givenPersistenceProcessor_whenAnyMethod_thenNoOp() {
        NoOpApplicationPersistenceProcessor processor = new NoOpApplicationPersistenceProcessor();
        ClientSessionCtx ctx = mock(ClientSessionCtx.class);

        assertThatCode(() -> {
            processor.startProcessingPersistedMessages(null);
            processor.startProcessingSharedSubscriptions(ctx, Set.of());
            processor.stopProcessingPersistedMessages("c");
            processor.stopProcessingSharedSubscriptions(ctx, Set.of());
            processor.clearPersistedMessages("c");
            processor.processPubAck("c", 1);
            processor.processPubRec(ctx, 1);
            processor.processPubRecNoPubRelDelivery(ctx, 1);
            processor.processPubComp("c", 1);
            processor.processChannelWritable(null);
            processor.processChannelNonWritable("c");
        }).doesNotThrowAnyException();
        verifyNoInteractions(ctx);
    }

    @Test
    void givenRemovedEventService_whenSend_thenNoOp() {
        assertThatCode(() -> new NoOpApplicationRemovedEventService().sendApplicationRemovedEvent("c")).doesNotThrowAnyException();
    }

}
