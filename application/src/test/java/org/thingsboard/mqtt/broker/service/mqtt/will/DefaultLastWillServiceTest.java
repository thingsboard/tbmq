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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.verification.VerificationMode;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLastWillServiceTest {

    MsgDispatcherService msgDispatcherService;
    RetainedMsgProcessor retainedMsgProcessor;
    StatsManager statsManager;
    DefaultLastWillService lastWillService;

    SessionInfo sessionInfo;
    UUID savedSessionId;

    @BeforeEach
    void setUp() {
        msgDispatcherService = mock(MsgDispatcherService.class);
        retainedMsgProcessor = mock(RetainedMsgProcessor.class);
        statsManager = mock(StatsManager.class);
        lastWillService = spy(new DefaultLastWillService(msgDispatcherService, retainedMsgProcessor, statsManager));

        sessionInfo = mock(SessionInfo.class);
        ClientInfo clientInfo = mock(ClientInfo.class);

        savedSessionId = UUID.randomUUID();

        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);
        when(sessionInfo.getSessionId()).thenReturn(savedSessionId);
    }

    @Test
    void testLastWillMsgNotSent() {
        saveLastWillMsg();

        removeAndExecuteLastWillIfNeeded(UUID.randomUUID());

        verifyPersistPublishMsg(never());
    }

    @Test
    void testLastWillMsgSent() {
        saveLastWillMsg();

        removeAndExecuteLastWillIfNeeded(savedSessionId);

        verifyPersistPublishMsg(times(1));
    }

    private void verifyPersistPublishMsg(VerificationMode mode) {
        verify(lastWillService, mode).persistPublishMsg(any(), any(), any());
    }

    private void saveLastWillMsg() {
        lastWillService.saveLastWillMsg(sessionInfo, getPublishMsg());
    }

    private PublishMsg getPublishMsg() {
        return PublishMsg.builder().build();
    }

    private void removeAndExecuteLastWillIfNeeded(UUID sessionId) {
        lastWillService.removeAndExecuteLastWillIfNeeded(sessionId, true);
    }
}