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
import org.thingsboard.mqtt.broker.config.RateLimitsConfiguration;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = RateLimitServiceImpl.class)
public class RateLimitServiceImplTest {

    private static final String CLIENT_ID = "test";

    @MockBean
    RateLimitsConfiguration rateLimitsConfiguration;
    @MockBean
    ClientSessionService clientSessionService;

    @SpyBean
    RateLimitServiceImpl rateLimitService;

    @Before
    public void setUp() throws Exception {
        rateLimitService.getClientLimits().put(CLIENT_ID, new TbRateLimits("1:1")); // limit 1 per 1 second
    }

    @After
    public void tearDown() throws Exception {
        Mockito.reset(rateLimitsConfiguration, clientSessionService);
    }

    @Test
    public void givenRateLimitsDisabled_whenCheckLimits_thenSuccess() {
        when(rateLimitsConfiguration.isEnabled()).thenReturn(false);

        boolean result = rateLimitService.checkLimits(CLIENT_ID, UUID.randomUUID(), null);
        Assert.assertTrue(result);
    }

    @Test
    public void givenRateLimitsEnabled_whenCheckLimits_thenGetExpectedResult() {
        when(rateLimitsConfiguration.isEnabled()).thenReturn(true);
        UUID sessionId = UUID.randomUUID();

        boolean first = rateLimitService.checkLimits(CLIENT_ID, sessionId, null);
        Assert.assertTrue(first);
        boolean second = rateLimitService.checkLimits(CLIENT_ID, sessionId, null);
        Assert.assertFalse(second);
        boolean third = rateLimitService.checkLimits(CLIENT_ID, sessionId, null);
        Assert.assertFalse(third);
    }

    @Test
    public void givenOneClient_whenRemoveIt_thenSuccess() {
        rateLimitService.remove(CLIENT_ID);
        assertEquals(0, rateLimitService.getClientLimits().size());
    }

    @Test
    public void givenOneClient_whenRemoveNull_thenSuccess() {
        rateLimitService.remove(null);
        assertEquals(1, rateLimitService.getClientLimits().size());
    }

    @Test
    public void givenNoSessionsLimit_whenCheckSessionsLimit_thenSuccess() {
        rateLimitService.setSessionsLimit(0);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertTrue(result);
    }

    @Test
    public void givenSessionsLimitReached_whenCheckSessionsLimit_thenFailure() {
        rateLimitService.setSessionsLimit(1);
        when(clientSessionService.getClientSessionsCount()).thenReturn(1);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertFalse(result);
    }

    @Test
    public void givenSessionsLimitNotReached_whenCheckSessionsLimit_thenSuccess() {
        rateLimitService.setSessionsLimit(5);
        when(clientSessionService.getClientSessionsCount()).thenReturn(1);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertTrue(result);
    }

    @Test
    public void givenSessionsLimitReached_whenCheckSessionsLimitForExistingClient_thenSuccess() {
        rateLimitService.setSessionsLimit(1);
        when(clientSessionService.getClientSessionsCount()).thenReturn(1);
        when(clientSessionService.getClientSessionInfo(CLIENT_ID)).thenReturn(ClientSessionInfo.builder().build());

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertTrue(result);
    }

}
