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
package org.thingsboard.mqtt.broker.service.limits;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.TbRateLimits;
import org.thingsboard.mqtt.broker.config.ClientsLimitProperties;
import org.thingsboard.mqtt.broker.config.DevicePersistedMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.IncomingRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.OutgoingRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.TotalMsgsRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = RateLimitServiceImpl.class)
public class RateLimitServiceImplTest {

    private static final String CLIENT_ID = "test";

    @MockitoBean
    IncomingRateLimitsConfiguration incomingRateLimitsConfiguration;
    @MockitoBean
    OutgoingRateLimitsConfiguration outgoingRateLimitsConfiguration;
    @MockitoBean
    DevicePersistedMsgsRateLimitsConfiguration devicePersistedMsgsRateLimitsConfiguration;
    @MockitoBean
    TotalMsgsRateLimitsConfiguration totalMsgsRateLimitsConfiguration;
    @MockitoBean
    RateLimitCacheService rateLimitCacheService;
    @MockitoBean
    ClientsLimitProperties clientsLimitProperties;

    @MockitoSpyBean
    RateLimitServiceImpl rateLimitService;

    @Before
    public void setUp() throws Exception {
        when(incomingRateLimitsConfiguration.isEnabled()).thenReturn(true);
        when(outgoingRateLimitsConfiguration.isEnabled()).thenReturn(true);
        when(devicePersistedMsgsRateLimitsConfiguration.isEnabled()).thenReturn(true);
        when(totalMsgsRateLimitsConfiguration.isEnabled()).thenReturn(true);

        rateLimitService.init();

        rateLimitService.getIncomingPublishClientLimits().put(CLIENT_ID, new TbRateLimits("1:1")); // limit 1 per 1 second
        rateLimitService.getOutgoingPublishClientLimits().put(CLIENT_ID, new TbRateLimits("1:1")); // limit 1 per 1 second
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void givenIncomingRateLimitsDisabled_whenCheckIncomingLimits_thenSuccess() {
        when(incomingRateLimitsConfiguration.isEnabled()).thenReturn(false);

        boolean result = rateLimitService.checkIncomingLimits(CLIENT_ID, UUID.randomUUID(), null);
        Assert.assertTrue(result);
    }

    @Test
    public void givenIncomingRateLimitsEnabled_whenCheckIncomingLimits_thenGetExpectedResult() {
        when(incomingRateLimitsConfiguration.isEnabled()).thenReturn(true);
        UUID sessionId = UUID.randomUUID();

        boolean first = rateLimitService.checkIncomingLimits(CLIENT_ID, sessionId, null);
        Assert.assertTrue(first);
        boolean second = rateLimitService.checkIncomingLimits(CLIENT_ID, sessionId, null);
        Assert.assertFalse(second);
        boolean third = rateLimitService.checkIncomingLimits(CLIENT_ID, sessionId, null);
        Assert.assertFalse(third);
    }

    @Test
    public void givenOutgoingRateLimitsDisabled_whenCheckOutgoingLimits_thenSuccess() {
        when(outgoingRateLimitsConfiguration.isEnabled()).thenReturn(false);

        boolean result = rateLimitService.checkOutgoingLimits(CLIENT_ID, null);
        Assert.assertTrue(result);
    }

    @Test
    public void givenOutgoingRateLimitsEnabled_whenCheckOutgoingLimitsWithQos0_thenGetExpectedResult() {
        when(outgoingRateLimitsConfiguration.isEnabled()).thenReturn(true);

        PublishMsgProto proto = PublishMsgProto.newBuilder().setQos(0).build();

        boolean first = rateLimitService.checkOutgoingLimits(CLIENT_ID, proto);
        Assert.assertTrue(first);
        boolean second = rateLimitService.checkOutgoingLimits(CLIENT_ID, proto);
        Assert.assertFalse(second);
        boolean third = rateLimitService.checkOutgoingLimits(CLIENT_ID, proto);
        Assert.assertFalse(third);
    }

    @Test
    public void givenOutgoingRateLimitsEnabled_whenCheckOutgoingLimitsWithQos1_thenGetExpectedResult() {
        when(outgoingRateLimitsConfiguration.isEnabled()).thenReturn(true);

        PublishMsgProto proto = PublishMsgProto.newBuilder().setQos(1).build();

        boolean first = rateLimitService.checkOutgoingLimits(CLIENT_ID, proto);
        Assert.assertTrue(first);
        boolean second = rateLimitService.checkOutgoingLimits(CLIENT_ID, proto);
        Assert.assertTrue(second);
        boolean third = rateLimitService.checkOutgoingLimits(CLIENT_ID, proto);
        Assert.assertTrue(third);
    }

    @Test
    public void givenOutgoingRateLimitsEnabled_whenCheckOutgoingLimitsWithQos2_thenGetExpectedResult() {
        when(outgoingRateLimitsConfiguration.isEnabled()).thenReturn(true);

        PublishMsgProto proto = PublishMsgProto.newBuilder().setQos(2).build();

        boolean first = rateLimitService.checkOutgoingLimits(CLIENT_ID, proto);
        Assert.assertTrue(first);
        boolean second = rateLimitService.checkOutgoingLimits(CLIENT_ID, proto);
        Assert.assertTrue(second);
        boolean third = rateLimitService.checkOutgoingLimits(CLIENT_ID, proto);
        Assert.assertTrue(third);
    }

    @Test
    public void givenOneClient_whenRemoveIt_thenSuccess() {
        rateLimitService.remove(CLIENT_ID);
        assertEquals(0, rateLimitService.getIncomingPublishClientLimits().size());
        assertEquals(0, rateLimitService.getOutgoingPublishClientLimits().size());
    }

    @Test
    public void givenOneClient_whenRemoveNull_thenSuccess() {
        rateLimitService.remove(null);
        assertEquals(1, rateLimitService.getIncomingPublishClientLimits().size());
        assertEquals(1, rateLimitService.getOutgoingPublishClientLimits().size());
    }

    @Test
    public void givenNoSessionsLimit_whenCheckSessionsLimit_thenSuccess() {
        when(clientsLimitProperties.isSessionsLimitDisabled()).thenReturn(true);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID, null);
        Assert.assertTrue(result);
    }

    @Test
    public void givenSessionsLimitReached_whenCheckSessionsLimit_thenFailure() {
        when(clientsLimitProperties.isSessionsLimitDisabled()).thenReturn(false);
        when(clientsLimitProperties.getSessionsLimit()).thenReturn(1);
        when(rateLimitCacheService.incrementSessionCount()).thenReturn(2L);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID, null);
        Assert.assertFalse(result);
    }

    @Test
    public void givenSessionsLimitNotReached_whenCheckSessionsLimit_thenSuccess() {
        when(clientsLimitProperties.isSessionsLimitDisabled()).thenReturn(false);
        when(clientsLimitProperties.getSessionsLimit()).thenReturn(5);
        when(rateLimitCacheService.incrementSessionCount()).thenReturn(2L);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID, null);
        Assert.assertTrue(result);
    }

    @Test
    public void givenSessionsLimitReached_whenCheckSessionsLimitForExistingClient_thenSuccess() {
        when(clientsLimitProperties.isSessionsLimitDisabled()).thenReturn(false);
        when(clientsLimitProperties.getSessionsLimit()).thenReturn(1);
        when(rateLimitCacheService.incrementSessionCount()).thenReturn(2L);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID, ClientSessionInfo.builder().build());
        Assert.assertTrue(result);
    }

    @Test
    public void givenNoApplicationClientsLimit_whenCheckIntegrationsLimit_thenSuccess() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(true);
        when(clientsLimitProperties.getApplicationClientsLimit()).thenReturn(0);

        boolean result = rateLimitService.checkIntegrationsLimit();
        Assert.assertTrue(result);
    }

    @Test
    public void givenApplicationClientsLimitReached_whenCheckIntegrationsLimit_thenFailure() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(false);
        when(clientsLimitProperties.getApplicationClientsLimit()).thenReturn(1);
        when(rateLimitCacheService.incrementApplicationClientsCount()).thenReturn(2L);

        boolean result = rateLimitService.checkIntegrationsLimit();
        Assert.assertFalse(result);
    }

    @Test
    public void givenApplicationClientsLimitNotReached_whenCheckIntegrationsLimit_thenSuccess() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(false);
        when(clientsLimitProperties.getApplicationClientsLimit()).thenReturn(5);
        when(rateLimitCacheService.incrementApplicationClientsCount()).thenReturn(2L);

        boolean result = rateLimitService.checkIntegrationsLimit();
        Assert.assertTrue(result);
    }

    @Test
    public void givenNoApplicationClientsLimit_whenCheckApplicationClientsLimit_thenSuccess() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(true);
        when(clientsLimitProperties.getApplicationClientsLimit()).thenReturn(0);

        SessionInfo sessionInfo = SessionInfo.builder().build();
        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo, null);
        Assert.assertTrue(result);
    }

    @Test
    public void givenApplicationClientsLimitAndNotPersistentAppClient_whenCheckApplicationClientsLimit_thenSuccess() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(false);
        when(clientsLimitProperties.getApplicationClientsLimit()).thenReturn(1);

        SessionInfo sessionInfo = SessionInfo.builder().clientInfo(ClientInfo.builder().type(ClientType.APPLICATION).build()).cleanStart(true).build();
        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo, null);
        Assert.assertTrue(result);
    }

    @Test
    public void givenApplicationClientsLimitReached_whenCheckApplicationClientsLimit_thenFailure() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(false);
        when(clientsLimitProperties.getApplicationClientsLimit()).thenReturn(1);
        when(rateLimitCacheService.incrementApplicationClientsCount()).thenReturn(2L);

        SessionInfo sessionInfo = SessionInfo.builder().clientInfo(ClientInfo.builder().type(ClientType.APPLICATION).clientId(CLIENT_ID).build()).cleanStart(false).build();
        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo, null);
        Assert.assertFalse(result);
    }

    @Test
    public void givenApplicationClientsLimitNotReached_whenCheckApplicationClientsLimit_thenSuccess() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(false);
        when(clientsLimitProperties.getApplicationClientsLimit()).thenReturn(5);
        when(rateLimitCacheService.incrementApplicationClientsCount()).thenReturn(2L);

        SessionInfo sessionInfo = SessionInfo.builder().clientInfo(ClientInfo.builder().type(ClientType.APPLICATION).clientId(CLIENT_ID).build()).cleanStart(false).build();
        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo, null);
        Assert.assertTrue(result);
    }

    @Test
    public void givenApplicationClientsLimitReached_whenCheckApplicationClientsLimitForExistingClient_thenSuccess() {
        when(clientsLimitProperties.isApplicationClientsLimitDisabled()).thenReturn(false);
        when(clientsLimitProperties.getApplicationClientsLimit()).thenReturn(1);
        when(rateLimitCacheService.incrementApplicationClientsCount()).thenReturn(2L);

        SessionInfo sessionInfo = SessionInfo.builder().clientInfo(ClientInfo.builder().type(ClientType.APPLICATION).clientId(CLIENT_ID).build()).cleanStart(false).build();
        ClientSessionInfo clientSessionInfo = ClientSessionInfo.builder().type(ClientType.APPLICATION).cleanStart(false).build();

        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo, clientSessionInfo);
        Assert.assertTrue(result);
    }

    @Test
    public void givenTokensAvailable_whenTryConsumeDevicePersistedMsgs_thenSuccess() {
        when(rateLimitCacheService.tryConsumeDevicePersistedMsgs(eq(10L))).thenReturn(10L);

        long tokens = rateLimitService.tryConsumeDevicePersistedMsgs(10L);
        assertEquals(10L, tokens);
    }

    @Test
    public void givenTokensAvailable_whenTryConsumeTotalMsgs_thenSuccess() {
        when(rateLimitCacheService.tryConsumeTotalMsgs(eq(10L))).thenReturn(10L);

        long tokens = rateLimitService.tryConsumeTotalMsgs(10L);
        assertEquals(10L, tokens);
    }
}
