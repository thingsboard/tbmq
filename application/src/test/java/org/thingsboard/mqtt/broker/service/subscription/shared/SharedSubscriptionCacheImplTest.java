package org.thingsboard.mqtt.broker.service.subscription.shared;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SharedSubscriptionCacheImplTest {

    private static final String CLIENT_ID_1 = "clientId1";
    private static final String CLIENT_ID_2 = "clientId2";

    ClientSessionCache clientSessionCache;
    SharedSubscriptionCacheImpl sharedSubscriptionCache;

    ClientSession clientSession1;
    ClientSession clientSession2;

    @Before
    public void setUp() {
        clientSessionCache = mock(ClientSessionCache.class);
        sharedSubscriptionCache = spy(new SharedSubscriptionCacheImpl(clientSessionCache));

        clientSession1 = mock(ClientSession.class);
        clientSession2 = mock(ClientSession.class);

        when(clientSessionCache.getClientSession(CLIENT_ID_1)).thenReturn(clientSession1);
        when(clientSessionCache.getClientSession(CLIENT_ID_2)).thenReturn(clientSession2);
    }

    @After
    public void destroy() {
        sharedSubscriptionCache.getSharedSubscriptionsMap().clear();
    }

    @Test
    public void testPutDifferentClients() {
        when(clientSession1.getClientType()).thenReturn(ClientType.APPLICATION);
        when(clientSession2.getClientType()).thenReturn(ClientType.APPLICATION);

        sharedSubscriptionCache.put(CLIENT_ID_1, List.of(
                new TopicSubscription("/test/topic/1", 1, "g1"),
                new TopicSubscription("/test/topic/2", 1, "g2"),
                new TopicSubscription("/test/topic/3", 1)
        ));

        assertEquals(2, sharedSubscriptionCache.getSharedSubscriptionsMap().size());

        SharedSubscriptions sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/1", "g1"));
        assertTrue(sharedSubscriptions.getDeviceSubscriptions().isEmpty());
        assertEquals(1, sharedSubscriptions.getApplicationSubscriptions().size());

        sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/2", "g2"));
        assertTrue(sharedSubscriptions.getDeviceSubscriptions().isEmpty());
        assertEquals(1, sharedSubscriptions.getApplicationSubscriptions().size());

        sharedSubscriptionCache.put(CLIENT_ID_2, List.of(
                new TopicSubscription("/test/topic/1", 2, "g1"),
                new TopicSubscription("/test/topic/2", 0, "g2"),
                new TopicSubscription("/test/topic/3", 1)
        ));

        assertEquals(2, sharedSubscriptionCache.getSharedSubscriptionsMap().size());

        sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/2", "g2"));
        assertTrue(sharedSubscriptions.getDeviceSubscriptions().isEmpty());
        assertEquals(2, sharedSubscriptions.getApplicationSubscriptions().size());
    }

    @Test
    public void testPutSameClientAndSubscription() {
        when(clientSession1.getClientType()).thenReturn(ClientType.DEVICE);
        when(clientSession1.getClientId()).thenReturn(CLIENT_ID_1);

        sharedSubscriptionCache.put(CLIENT_ID_1, List.of(
                new TopicSubscription("/test/topic/1", 1, "g1")
        ));

        assertEquals(1, sharedSubscriptionCache.getSharedSubscriptionsMap().size());

        SharedSubscriptions sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/1", "g1"));
        assertTrue(sharedSubscriptions.getApplicationSubscriptions().isEmpty());
        assertEquals(1, sharedSubscriptions.getDeviceSubscriptions().size());

        sharedSubscriptionCache.put(CLIENT_ID_1, List.of(
                new TopicSubscription("/test/topic/1", 2, "g1")
        ));

        assertEquals(1, sharedSubscriptionCache.getSharedSubscriptionsMap().size());

        sharedSubscriptions = sharedSubscriptionCache.getSharedSubscriptionsMap()
                .get(new TopicSharedSubscription("/test/topic/1", "g1"));
        assertTrue(sharedSubscriptions.getApplicationSubscriptions().isEmpty());
        assertEquals(1, sharedSubscriptions.getDeviceSubscriptions().size());
    }

    @Test
    public void remove() {

    }

    @Test
    public void testGetNothing() {
        Assert.assertNull(sharedSubscriptionCache.get(null));
        Assert.assertNull(sharedSubscriptionCache.get(Set.of()));
    }

    @Test
    public void testGet() {
        when(clientSession1.getClientType()).thenReturn(ClientType.DEVICE);
        when(clientSession1.getClientId()).thenReturn(CLIENT_ID_1);

        when(clientSession2.getClientType()).thenReturn(ClientType.DEVICE);
        when(clientSession2.getClientId()).thenReturn(CLIENT_ID_2);

        sharedSubscriptionCache.put(CLIENT_ID_1, List.of(
                new TopicSubscription("/test/topic/1", 2, "g1"),
                new TopicSubscription("#", 0, "g2"),
                new TopicSubscription("/test/topic/+", 1, "g3")
        ));

        sharedSubscriptionCache.put(CLIENT_ID_2, List.of(
                new TopicSubscription("/test/topic/1", 1, "g1")
        ));

        SharedSubscriptions sharedSubscriptions = sharedSubscriptionCache.get(Set.of(
                new TopicSharedSubscription("/test/topic/1", "g1"),
                new TopicSharedSubscription("#", "g2"),
                new TopicSharedSubscription("/test/topic/+", "g3")
        ));

        Assert.assertEquals(2, sharedSubscriptions.getDeviceSubscriptions().size());
        for (Subscription subscription : sharedSubscriptions.getDeviceSubscriptions()) {
            assertEquals("/test/topic/1", subscription.getTopicFilter());
            if (subscription.getClientSession().getClientId().equals(CLIENT_ID_1)) {
                assertEquals(2, subscription.getQos());
            } else {
                assertEquals(1, subscription.getQos());
            }
        }
    }
}