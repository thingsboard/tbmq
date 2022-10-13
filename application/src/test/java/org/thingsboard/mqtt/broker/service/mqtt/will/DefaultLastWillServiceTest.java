/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.verification.VerificationMode;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    DefaultLastWillService lastWillService;

    SessionInfo sessionInfo;
    UUID savedSessionId;

    @Before
    public void setUp() {
        msgDispatcherService = mock(MsgDispatcherService.class);
        retainedMsgProcessor = mock(RetainedMsgProcessor.class);
        statsManager = mock(StatsManager.class);
        lastWillService = spy(new DefaultLastWillService(msgDispatcherService, retainedMsgProcessor, statsManager));

        sessionInfo = mock(SessionInfo.class);
        savedSessionId = UUID.randomUUID();
        when(sessionInfo.getSessionId()).thenReturn(savedSessionId);
    }

    @Test
    public void testLastWillMsgNotSent() {
        saveLastWillMsg();

        removeAndExecuteLastWillIfNeeded(UUID.randomUUID());

        verifyPersistPublishMsg(never());
    }

    @Test
    public void testLastWillMsgSent() {
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        lastWillService.setScheduler(scheduledExecutorService);
        doNothing().when(lastWillService).scheduleLastWill(any(), any(), anyInt());

        saveLastWillMsg();

        removeAndExecuteLastWillIfNeeded(savedSessionId);

        verifyPersistPublishMsg(times(1));
    }

    private void verifyPersistPublishMsg(VerificationMode mode) {
        verify(lastWillService, mode).scheduleLastWill(any(), any(), anyInt());
    }

    private void saveLastWillMsg() {
        lastWillService.saveLastWillMsg(sessionInfo, getPublishMsg());
    }

    private PublishMsg getPublishMsg() {
        MqttProperties properties = new MqttProperties();
        int propertyId = MqttProperties.MqttPropertyType.WILL_DELAY_INTERVAL.value();
        properties.add(new MqttProperties.IntegerProperty(propertyId, 100));
        return PublishMsg
                .builder()
                .properties(properties)
                .build();
    }

    private void removeAndExecuteLastWillIfNeeded(UUID sessionId) {
        lastWillService.removeAndExecuteLastWillIfNeeded(sessionId, true, false);
    }
}