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
package org.thingsboard.mqtt.broker.service.mqtt.will;

import io.netty.handler.codec.mqtt.MqttProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.verification.VerificationMode;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultLastWillServiceTest {

    MsgDispatcherService msgDispatcherService;
    RetainedMsgProcessor retainedMsgProcessor;
    StatsManager statsManager;
    TbMessageStatsReportClient tbMessageStatsReportClient;
    DefaultLastWillService lastWillService;

    SessionInfo sessionInfo;
    UUID savedSessionId;

    @Before
    public void setUp() {
        msgDispatcherService = mock(MsgDispatcherService.class);
        retainedMsgProcessor = mock(RetainedMsgProcessor.class);
        statsManager = mock(StatsManager.class);
        tbMessageStatsReportClient = mock(TbMessageStatsReportClient.class);
        lastWillService = spy(new DefaultLastWillService(msgDispatcherService, retainedMsgProcessor, statsManager, tbMessageStatsReportClient));

        sessionInfo = mock(SessionInfo.class);
        savedSessionId = UUID.randomUUID();
        when(sessionInfo.getSessionId()).thenReturn(savedSessionId);

        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        lastWillService.setScheduler(scheduledExecutorService);
        doNothing().when(lastWillService).scheduleLastWill(any(), anyInt());
    }

    @Test
    public void testNoLastWillMsgNotSent() {
        saveLastWillMsg();

        removeAndExecuteLastWillIfNeeded(UUID.randomUUID(), true);

        verifyPersistPublishMsg(never());
    }

    @Test
    public void testLastWillMsgSent() {
        saveLastWillMsg();

        removeAndExecuteLastWillIfNeeded(savedSessionId, true);

        verifyPersistPublishMsg(times(1));
    }

    @Test
    public void testLastWillMsgNotSent() {
        saveLastWillMsg();

        removeAndExecuteLastWillIfNeeded(savedSessionId, false);

        verifyPersistPublishMsg(never());
    }

    @Test
    public void givenLastWillPersistFails_whenCallbackOnFailure_thenReportsDroppedMsg() {
        TbQueueCallback callback = persistPublishMsgAndCaptureCallback();

        callback.onFailure(new RuntimeException("Kafka is not available"));

        verify(tbMessageStatsReportClient, times(1)).reportDroppedMsgs();
    }

    @Test
    public void givenLastWillPersistSucceeds_whenCallbackOnSuccess_thenDoesNotReportDroppedMsg() {
        TbQueueCallback callback = persistPublishMsgAndCaptureCallback();

        callback.onSuccess(null);

        verify(tbMessageStatsReportClient, never()).reportDroppedMsgs();
    }

    private TbQueueCallback persistPublishMsgAndCaptureCallback() {
        lastWillService.persistPublishMsg(sessionInfo, getPublishMsg(), null);

        ArgumentCaptor<TbQueueCallback> callbackCaptor = ArgumentCaptor.forClass(TbQueueCallback.class);
        verify(msgDispatcherService).persistPublishMsg(eq(sessionInfo), any(PublishMsg.class), isNull(), callbackCaptor.capture());
        return callbackCaptor.getValue();
    }

    private void verifyPersistPublishMsg(VerificationMode mode) {
        verify(lastWillService, mode).scheduleLastWill(any(), anyInt());
    }

    private void saveLastWillMsg() {
        lastWillService.saveLastWillMsg(sessionInfo, getPublishMsg(), null);
    }

    private PublishMsg getPublishMsg() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.WILL_DELAY_INTERVAL_PROP_ID, 100));
        return PublishMsg
                .builder()
                .properties(properties)
                .build();
    }

    private void removeAndExecuteLastWillIfNeeded(UUID sessionId, boolean newSessionCleanStart) {
        MqttDisconnectMsg disconnectMsg = new MqttDisconnectMsg(sessionId, new DisconnectReason(DisconnectReasonType.ON_KEEP_ALIVE), newSessionCleanStart);
        lastWillService.removeAndExecuteLastWillIfNeeded(disconnectMsg, -1);
    }
}
