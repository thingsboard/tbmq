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
package org.thingsboard.mqtt.broker.actors.client.service.channel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorState;
import org.thingsboard.mqtt.broker.actors.client.state.SessionState;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceProcessor;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ChannelBackpressureManagerImpl.class)
public class ChannelBackpressureManagerImplTest {

    @MockBean
    ApplicationPersistenceProcessor applicationPersistenceProcessor;
    @MockBean
    DevicePersistenceProcessor devicePersistenceProcessor;
    @MockBean
    StatsManager statsManager;

    @SpyBean
    ChannelBackpressureManagerImpl channelBackpressureManager;

    ClientActorState state;
    ClientSessionCtx ctx;
    String clientId = "id";

    @Before
    public void setUp() throws Exception {
        state = mock(ClientActorState.class);
        ctx = mock(ClientSessionCtx.class);

        when(state.getClientId()).thenReturn(clientId);
        when(state.getCurrentSessionCtx()).thenReturn(ctx);
        when(ctx.isCleanSession()).thenReturn(false);

        when(statsManager.createNonWritableClientsCounter()).thenReturn(new AtomicInteger());
        channelBackpressureManager.init();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void givenAppClientState_whenOnChannelWritable_thenVerifyAppProcessorExecutions() {
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CHANNEL_NON_WRITABLE);

        channelBackpressureManager.onChannelWritable(state);

        verify(applicationPersistenceProcessor).processChannelWritable(eq(state));
        assertThatCounterIs(0);
    }

    @Test
    public void givenDevClientState_whenOnChannelWritable_thenVerifyDevProcessorExecutions() {
        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CHANNEL_NON_WRITABLE);

        channelBackpressureManager.onChannelWritable(state);

        verify(devicePersistenceProcessor).processChannelWritable(eq(clientId));
        assertThatCounterIs(0);
    }

    @Test
    public void givenAppClientWithWritableState_whenOnChannelWritable_thenVerifyAppProcessorExecutions() {
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CONNECTED);

        channelBackpressureManager.onChannelWritable(state);

        verify(applicationPersistenceProcessor, never()).processChannelWritable(eq(state));
        assertThatCounterIs(0);
    }

    @Test
    public void givenDevClientWithWritableState_whenOnChannelWritable_thenVerifyDevProcessorExecutions() {
        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CONNECTED);

        channelBackpressureManager.onChannelWritable(state);

        verify(devicePersistenceProcessor, never()).processChannelWritable(eq(clientId));
        assertThatCounterIs(0);
    }

    @Test
    public void givenAppClientState_whenOnChannelNonWritable_thenVerifyAppProcessorExecutions() {
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CONNECTED);

        channelBackpressureManager.onChannelNonWritable(state);

        verify(applicationPersistenceProcessor).processChannelNonWritable(eq(clientId));
        assertThatCounterIs(1);
    }

    @Test
    public void givenDevClientState_whenOnChannelNonWritable_thenVerifyDevProcessorExecutions() {
        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CONNECTED);

        channelBackpressureManager.onChannelNonWritable(state);

        verify(devicePersistenceProcessor).processChannelNonWritable(eq(clientId));
        assertThatCounterIs(1);
    }

    @Test
    public void givenNonPersistentAppClientState_whenOnChannelWritable_thenVerifyAppProcessorExecutions() {
        setCleanSession();
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CHANNEL_NON_WRITABLE);

        channelBackpressureManager.onChannelWritable(state);

        verify(applicationPersistenceProcessor, never()).processChannelWritable(eq(state));
        assertThatCounterIs(0);
    }

    @Test
    public void givenNonPersistentDevClientState_whenOnChannelWritable_thenVerifyDevProcessorExecutions() {
        setCleanSession();
        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CHANNEL_NON_WRITABLE);

        channelBackpressureManager.onChannelWritable(state);

        verify(devicePersistenceProcessor, never()).processChannelWritable(eq(clientId));
        assertThatCounterIs(0);
    }

    @Test
    public void givenNonPersistentAppClientState_whenOnChannelNonWritable_thenVerifyAppProcessorExecutions() {
        setCleanSession();
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CONNECTED);

        channelBackpressureManager.onChannelNonWritable(state);

        verify(applicationPersistenceProcessor, never()).processChannelNonWritable(eq(clientId));
        assertThatCounterIs(1);
    }

    @Test
    public void givenNonPersistentDevClientState_whenOnChannelNonWritable_thenVerifyDevProcessorExecutions() {
        setCleanSession();
        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CONNECTED);

        channelBackpressureManager.onChannelNonWritable(state);

        verify(devicePersistenceProcessor, never()).processChannelNonWritable(eq(clientId));
        assertThatCounterIs(1);
    }

    @Test
    public void givenNonPersistentAppClientWithNonWritableState_whenOnChannelNonWritable_thenVerifyAppProcessorExecutions() {
        setCleanSession();
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CHANNEL_NON_WRITABLE);

        channelBackpressureManager.onChannelNonWritable(state);

        verify(applicationPersistenceProcessor, never()).processChannelNonWritable(eq(clientId));
        assertThatCounterIs(0);
    }

    @Test
    public void givenNonPersistentDevClientWithNonWritableState_whenOnChannelNonWritable_thenVerifyDevProcessorExecutions() {
        setCleanSession();
        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);
        when(state.getCurrentSessionState()).thenReturn(SessionState.CHANNEL_NON_WRITABLE);

        channelBackpressureManager.onChannelNonWritable(state);

        verify(devicePersistenceProcessor, never()).processChannelNonWritable(eq(clientId));
        assertThatCounterIs(0);
    }

    private void assertThatCounterIs(int expected) {
        assertThat(channelBackpressureManager.getNonWritableClientsCount().get()).isEqualTo(expected);
    }

    private void setCleanSession() {
        when(ctx.isCleanSession()).thenReturn(true);
    }

}
