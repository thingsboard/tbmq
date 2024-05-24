/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.util.TbRateLimits;
import org.thingsboard.mqtt.broker.config.IncomingRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.config.OutgoingRateLimitsConfiguration;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = RateLimitServiceImpl.class)
public class RateLimitServiceImplTest {

    private static final String CLIENT_ID = "test";

    @MockBean
    IncomingRateLimitsConfiguration incomingRateLimitsConfiguration;
    @MockBean
    OutgoingRateLimitsConfiguration outgoingRateLimitsConfiguration;
    @MockBean
    ClientSessionService clientSessionService;
    @MockBean
    RateLimitCacheService rateLimitCacheService;

    @SpyBean
    RateLimitServiceImpl rateLimitService;

    @Before
    public void setUp() throws Exception {
        when(incomingRateLimitsConfiguration.isEnabled()).thenReturn(true);
        when(outgoingRateLimitsConfiguration.isEnabled()).thenReturn(true);

        rateLimitService.init();

        rateLimitService.getIncomingPublishClientLimits().put(CLIENT_ID, new TbRateLimits("1:1")); // limit 1 per 1 second
        rateLimitService.getOutgoingPublishClientLimits().put(CLIENT_ID, new TbRateLimits("1:1")); // limit 1 per 1 second
    }

    @After
    public void tearDown() throws Exception {
        Mockito.reset(incomingRateLimitsConfiguration, outgoingRateLimitsConfiguration, clientSessionService);
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

        QueueProtos.PublishMsgProto proto = QueueProtos.PublishMsgProto.newBuilder().setQos(0).build();

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

        QueueProtos.PublishMsgProto proto = QueueProtos.PublishMsgProto.newBuilder().setQos(1).build();

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

        QueueProtos.PublishMsgProto proto = QueueProtos.PublishMsgProto.newBuilder().setQos(2).build();

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
    @Ignore
    public void givenNoSessionsLimit_whenCheckSessionsLimit_thenSuccess() {
        rateLimitService.setSessionsLimit(0);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertTrue(result);
    }

    @Test
    @Ignore
    public void givenSessionsLimitReached_whenCheckSessionsLimit_thenFailure() {
        rateLimitService.setSessionsLimit(1);
        when(clientSessionService.getClientSessionsCount()).thenReturn(1);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertFalse(result);
    }

    @Test
    @Ignore
    public void givenSessionsLimitNotReached_whenCheckSessionsLimit_thenSuccess() {
        rateLimitService.setSessionsLimit(5);
        when(clientSessionService.getClientSessionsCount()).thenReturn(1);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertTrue(result);
    }

    @Test
    @Ignore
    public void givenSessionsLimitReached_whenCheckSessionsLimitForExistingClient_thenSuccess() {
        rateLimitService.setSessionsLimit(1);
        when(clientSessionService.getClientSessionsCount()).thenReturn(1);
        when(clientSessionService.getClientSessionInfo(CLIENT_ID)).thenReturn(ClientSessionInfo.builder().build());

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertTrue(result);
    }

}
