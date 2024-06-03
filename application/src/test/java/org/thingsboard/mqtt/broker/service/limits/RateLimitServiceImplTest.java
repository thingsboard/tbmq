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
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
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
        Mockito.reset(incomingRateLimitsConfiguration, outgoingRateLimitsConfiguration, clientSessionService, rateLimitCacheService);
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
    public void givenNoSessionsLimit_whenCheckSessionsLimit_thenSuccess() {
        rateLimitService.setSessionsLimit(0);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertTrue(result);
    }

    @Test
    public void givenSessionsLimitReached_whenCheckSessionsLimit_thenFailure() {
        rateLimitService.setSessionsLimit(1);
        when(rateLimitCacheService.incrementSessionCount()).thenReturn(2L);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertFalse(result);
    }

    @Test
    public void givenSessionsLimitNotReached_whenCheckSessionsLimit_thenSuccess() {
        rateLimitService.setSessionsLimit(5);
        when(rateLimitCacheService.incrementSessionCount()).thenReturn(2L);

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertTrue(result);
    }

    @Test
    public void givenSessionsLimitReached_whenCheckSessionsLimitForExistingClient_thenSuccess() {
        rateLimitService.setSessionsLimit(1);
        when(rateLimitCacheService.incrementSessionCount()).thenReturn(2L);
        when(clientSessionService.getClientSessionInfo(CLIENT_ID)).thenReturn(ClientSessionInfo.builder().build());

        boolean result = rateLimitService.checkSessionsLimit(CLIENT_ID);
        Assert.assertTrue(result);
    }

    @Test
    public void givenNoApplicationClientsLimit_whenCheckApplicationClientsLimit_thenSuccess() {
        rateLimitService.setApplicationClientsLimit(0);

        SessionInfo sessionInfo = SessionInfo.builder().build();
        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo);
        Assert.assertTrue(result);
    }

    @Test
    public void givenApplicationClientsLimitAndNotPersistentAppClient_whenCheckApplicationClientsLimit_thenSuccess() {
        rateLimitService.setApplicationClientsLimit(1);

        SessionInfo sessionInfo = SessionInfo.builder().clientInfo(ClientInfo.builder().type(ClientType.APPLICATION).build()).cleanStart(true).build();
        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo);
        Assert.assertTrue(result);
    }

    @Test
    public void givenApplicationClientsLimitReached_whenCheckApplicationClientsLimit_thenFailure() {
        rateLimitService.setApplicationClientsLimit(1);
        when(rateLimitCacheService.incrementApplicationClientsCount()).thenReturn(2L);

        SessionInfo sessionInfo = SessionInfo.builder().clientInfo(ClientInfo.builder().type(ClientType.APPLICATION).clientId(CLIENT_ID).build()).cleanStart(false).build();
        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo);
        Assert.assertFalse(result);
    }

    @Test
    public void givenApplicationClientsLimitNotReached_whenCheckApplicationClientsLimit_thenSuccess() {
        rateLimitService.setApplicationClientsLimit(5);
        when(rateLimitCacheService.incrementApplicationClientsCount()).thenReturn(2L);

        SessionInfo sessionInfo = SessionInfo.builder().clientInfo(ClientInfo.builder().type(ClientType.APPLICATION).clientId(CLIENT_ID).build()).cleanStart(false).build();
        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo);
        Assert.assertTrue(result);
    }

    @Test
    public void givenApplicationClientsLimitReached_whenCheckApplicationClientsLimitForExistingClient_thenSuccess() {
        rateLimitService.setApplicationClientsLimit(1);
        when(rateLimitCacheService.incrementApplicationClientsCount()).thenReturn(2L);

        SessionInfo sessionInfo = SessionInfo.builder().clientInfo(ClientInfo.builder().type(ClientType.APPLICATION).clientId(CLIENT_ID).build()).cleanStart(false).build();
        ClientSessionInfo clientSessionInfo = ClientSessionInfo.builder().type(ClientType.APPLICATION).cleanStart(false).build();
        when(clientSessionService.getClientSessionInfo(CLIENT_ID)).thenReturn(clientSessionInfo);

        boolean result = rateLimitService.checkApplicationClientsLimit(sessionInfo);
        Assert.assertTrue(result);
    }
}
