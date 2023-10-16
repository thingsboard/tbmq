/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.processing;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.processing.data.MsgSubscriptions;
import org.thingsboard.mqtt.broker.service.processing.data.PersistentMsgSubscriptions;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategyFactory;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MsgDispatcherServiceImpl.class)
public class MsgDispatcherServiceImplTest {

    static final String TOPIC = "topic";

    @MockBean
    SubscriptionService subscriptionService;
    @MockBean
    StatsManager statsManager;
    @MockBean
    MsgPersistenceManager msgPersistenceManager;
    @MockBean
    ClientSessionCache clientSessionCache;
    @MockBean
    DownLinkProxy downLinkProxy;
    @MockBean
    ClientLogger clientLogger;
    @MockBean
    PublishMsgQueuePublisher publishMsgQueuePublisher;
    @MockBean
    SharedSubscriptionProcessingStrategyFactory sharedSubscriptionProcessingStrategyFactory;
    @MockBean
    SharedSubscriptionCacheService sharedSubscriptionCacheService;
    @MockBean
    TbMessageStatsReportClient tbMessageStatsReportClient;
    @MockBean
    ServiceInfoProvider serviceInfoProvider;
    @SpyBean
    MsgDispatcherServiceImpl msgDispatcherService;

    ClientSessionInfo clientSessionInfo;

    @Before
    public void setUp() {
        clientSessionInfo = mock(ClientSessionInfo.class);
    }

    @Test
    public void testGetSubscriptionWithHigherQos() {
        ClientSubscription clientSubscription1 = newClientSubscription("id", 1, null);
        ClientSubscription clientSubscription2 = newClientSubscription("id", 0, null);

        ValueWithTopicFilter<ClientSubscription> value1 = new ValueWithTopicFilter<>(clientSubscription1, "#");
        ValueWithTopicFilter<ClientSubscription> value2 = new ValueWithTopicFilter<>(clientSubscription2, "test/+");

        ValueWithTopicFilter<ClientSubscription> result =
                msgDispatcherService.getSubscriptionWithHigherQos(value1, value2);

        assertEquals(result, value1);
    }

    @Test
    public void testConvertToSharedSubscriptionList() {
        Set<Subscription> subscriptions = Set.of(
                newSubscription(0, "group1"),
                newSubscription(1, "group1"),
                newSubscription(2, "group2"),
                newSubscription(0, "group2"),
                newSubscription(1, "group3"),
                newSubscription(2, "group3")
        );
        List<SharedSubscription> sharedSubscriptionList = msgDispatcherService.toSharedSubscriptionList(subscriptions);
        assertEquals(3, sharedSubscriptionList.size());
        sharedSubscriptionList.forEach(sharedSubscription -> assertEquals(2, sharedSubscription.getSubscriptions().size()));
    }

    @Test
    public void testFilterHighestQosClientSubscriptions4() {
        List<ValueWithTopicFilter<ClientSubscription>> before = List.of(
                new ValueWithTopicFilter<>(
                        new ClientSubscription(
                                "clientId1",
                                0,
                                null,
                                new SubscriptionOptions(
                                        true,
                                        false,
                                        SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE)),
                        "+/test/+")
        );
        assertEquals(1, before.size());

        Collection<ValueWithTopicFilter<ClientSubscription>> result = msgDispatcherService.filterClientSubscriptions(before, "clientId1");
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFilterHighestQosClientSubscriptions3() {
        List<ValueWithTopicFilter<ClientSubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 0, "+/test/+")
        );
        assertEquals(1, before.size());

        Collection<ValueWithTopicFilter<ClientSubscription>> result = msgDispatcherService.filterClientSubscriptions(before, null);
        assertEquals(1, result.size());

        assertTrue(result.contains(
                newValueWithTopicFilter("clientId1", 0, "+/test/+")
        ));
    }

    @Test
    public void testFilterHighestQosClientSubscriptions2() {
        List<ValueWithTopicFilter<ClientSubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 0, "+/test/+"),
                newValueWithTopicFilter("clientId2", 0, "#"),
                newValueWithTopicFilter("clientId3", 2, "topic/+/+"),
                newValueWithTopicFilter("clientId1", 2, "+/+/res"),
                newValueWithTopicFilter("clientId2", 1, "topic/test/+"),
                newValueWithTopicFilter("clientId3", 0, "topic/+/res")
        );
        assertEquals(6, before.size());

        Collection<ValueWithTopicFilter<ClientSubscription>> result = msgDispatcherService.filterClientSubscriptions(before, null);
        assertEquals(3, result.size());

        assertTrue(result.containsAll(
                List.of(
                        newValueWithTopicFilter("clientId3", 2, "topic/+/+"),
                        newValueWithTopicFilter("clientId1", 2, "+/+/res"),
                        newValueWithTopicFilter("clientId2", 1, "topic/test/+")
                )
        ));
    }

    @Test
    public void testFilterHighestQosClientSubscriptions1() {
        List<ValueWithTopicFilter<ClientSubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 1, "+/test/+"),
                newValueWithTopicFilter("clientId2", 1, "#"),
                newValueWithTopicFilter("clientId3", 1, "topic/+/+"),
                newValueWithTopicFilter("clientId4", 1, "+/+/res"),
                newValueWithTopicFilter("clientId5", 1, "topic/test/+"),
                newValueWithTopicFilter("clientId6", 1, "topic/+/res")
        );
        assertEquals(6, before.size());

        Collection<ValueWithTopicFilter<ClientSubscription>> result = msgDispatcherService.filterClientSubscriptions(before, null);
        assertEquals(6, result.size());
    }

    @Test
    public void testFindAnyConnectedSubscription() {
        List<Subscription> subscriptions = List.of(
                new Subscription("topic1", 1, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic2", 0, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic3", 2, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic4", 0, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic5", 1, ClientSessionInfo.builder().connected(false).build())
        );
        Subscription subscription = msgDispatcherService.findAnyConnectedSubscription(subscriptions);
        assertNull(subscription);

        subscriptions = List.of(
                new Subscription("topic1", 1, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic2", 0, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic3", 2, ClientSessionInfo.builder().connected(true).build()),
                new Subscription("topic4", 0, ClientSessionInfo.builder().connected(false).build()),
                new Subscription("topic5", 1, ClientSessionInfo.builder().connected(false).build())
        );
        subscription = msgDispatcherService.findAnyConnectedSubscription(subscriptions);
        assertEquals("topic3", subscription.getTopicFilter());
    }

    @Test
    public void testGetAllSubscriptionsForPubMsg() {
        ClientSessionInfo clientSessionInfo1 = mock(ClientSessionInfo.class);
        ClientSessionInfo clientSessionInfo2 = mock(ClientSessionInfo.class);
        ClientSessionInfo clientSessionInfo3 = mock(ClientSessionInfo.class);
        ClientSessionInfo clientSessionInfo4 = mock(ClientSessionInfo.class);

        mockClientSessionGetClientId(clientSessionInfo1, "clientId1");
        mockClientSessionGetClientId(clientSessionInfo2, "clientId2");
        mockClientSessionGetClientId(clientSessionInfo3, "clientId3");
        mockClientSessionGetClientId(clientSessionInfo4, "clientId4");

        mockClientSessionCacheGetClientSession("clientId1", clientSessionInfo1);
        mockClientSessionCacheGetClientSession("clientId2", clientSessionInfo2);
        mockClientSessionCacheGetClientSession("clientId3", clientSessionInfo3);
        mockClientSessionCacheGetClientSession("clientId4", clientSessionInfo4);

        var topic = "topic/test";
        QueueProtos.PublishMsgProto publishMsgProto = QueueProtos.PublishMsgProto
                .newBuilder()
                .setTopicName(topic)
                .build();

        when(sharedSubscriptionCacheService.sharedSubscriptionsInitialized()).thenReturn(true);

        when(sharedSubscriptionCacheService.get(
                Set.of(
                        new TopicSharedSubscription("topic/+", "g1")
                )
        )).thenReturn(
                new SharedSubscriptions(
                        Set.of(
                                new Subscription("topic/+", 1, clientSessionInfo1, "g1", SubscriptionOptions.newInstance()),
                                new Subscription("topic/+", 1, clientSessionInfo2, "g1", SubscriptionOptions.newInstance())
                        ),
                        Set.of()
                )
        );

        when(subscriptionService.getSubscriptions(topic)).thenReturn(List.of(
                newValueWithTopicFilter("clientId1", 0, "g1", "topic/+"),
                newValueWithTopicFilter("clientId2", 0, "g1", "topic/+"),
                newValueWithTopicFilter("clientId3", 1, "topic/#"),
                newValueWithTopicFilter("clientId4", 2, "#")
        ));

        MsgSubscriptions msgSubscriptions = msgDispatcherService.getAllSubscriptionsForPubMsg(publishMsgProto, "clientId");

        assertNull(msgSubscriptions.getTargetDeviceSharedSubscriptions());
        assertEquals(2, msgSubscriptions.getAllApplicationSharedSubscriptions().size());
        assertEquals(2, msgSubscriptions.getCommonSubscriptions().size());

        List<String> commonClientIds = getClientIds(msgSubscriptions.getCommonSubscriptions().stream());
        assertTrue(commonClientIds.containsAll(List.of("clientId3", "clientId4")));

        List<String> appClientIds = getClientIds(msgSubscriptions.getAllApplicationSharedSubscriptions().stream());
        assertTrue(appClientIds.containsAll(List.of("clientId1", "clientId2")));
    }

    @Test
    public void testProcessBasicAndCollectPersistentSubscriptionsWhenNoSubscriptions() {
        MsgSubscriptions msgSubscriptions = new MsgSubscriptions(
                null, null, null
        );

        QueueProtos.PublishMsgProto publishMsgProto = QueueProtos.PublishMsgProto
                .newBuilder()
                .setTopicName("topic/test")
                .build();

        PersistentMsgSubscriptions persistentMsgSubscriptions =
                msgDispatcherService.processBasicAndCollectPersistentSubscriptions(msgSubscriptions, publishMsgProto);

        assertNull(persistentMsgSubscriptions.getDeviceSubscriptions());
        assertNull(persistentMsgSubscriptions.getApplicationSubscriptions());
        assertNull(persistentMsgSubscriptions.getAllApplicationSharedSubscriptions());
    }

    @Test
    public void testProcessBasicAndCollectPersistentSubscriptions() {
        ClientSessionInfo clientSessionInfo1 = mock(ClientSessionInfo.class);
        ClientSessionInfo clientSessionInfo2 = mock(ClientSessionInfo.class);
        ClientSessionInfo clientSessionInfo3 = mock(ClientSessionInfo.class);
        ClientSessionInfo clientSessionInfo4 = mock(ClientSessionInfo.class);
        ClientSessionInfo clientSessionInfo5 = mock(ClientSessionInfo.class);

        when(clientSessionInfo1.isPersistent()).thenReturn(true);
        when(clientSessionInfo2.isPersistent()).thenReturn(true);
        when(clientSessionInfo5.isPersistent()).thenReturn(false);

        when(clientSessionInfo1.getType()).thenReturn(ClientType.APPLICATION);
        when(clientSessionInfo2.getType()).thenReturn(ClientType.APPLICATION);
        when(clientSessionInfo5.getType()).thenReturn(ClientType.DEVICE);

        mockClientSessionGetClientId(clientSessionInfo1, "clientId1");
        mockClientSessionGetClientId(clientSessionInfo2, "clientId2");

        MsgSubscriptions msgSubscriptions = new MsgSubscriptions(
                List.of(
                        new Subscription("test/topic/1", 1, clientSessionInfo1),
                        new Subscription("test/+/1", 2, clientSessionInfo2)
                ),
                Set.of(
                        new Subscription("#", 2, clientSessionInfo3),
                        new Subscription("test/#", 0, clientSessionInfo4)
                ),
                List.of(
                        new Subscription("test/topic/#", 1, clientSessionInfo5)
                )
        );
        QueueProtos.PublishMsgProto publishMsgProto = QueueProtos.PublishMsgProto
                .newBuilder()
                .setTopicName("topic/test")
                .setQos(2)
                .build();

        PersistentMsgSubscriptions persistentMsgSubscriptions =
                msgDispatcherService.processBasicAndCollectPersistentSubscriptions(msgSubscriptions, publishMsgProto);

        assertEquals(persistentMsgSubscriptions.getAllApplicationSharedSubscriptions(), msgSubscriptions.getAllApplicationSharedSubscriptions());
        assertTrue(persistentMsgSubscriptions.getDeviceSubscriptions().isEmpty());
        assertEquals(2, persistentMsgSubscriptions.getApplicationSubscriptions().size());
        List<String> appClientIds = getClientIds(persistentMsgSubscriptions.getApplicationSubscriptions().stream());
        assertTrue(appClientIds.containsAll(List.of("clientId1", "clientId2")));
    }

    private List<String> getClientIds(Stream<Subscription> msgSubscriptions) {
        return msgSubscriptions
                .map(subscription -> subscription.getClientSessionInfo().getClientId())
                .collect(Collectors.toList());
    }

    private void mockClientSessionCacheGetClientSession(String clientId, ClientSessionInfo clientSessionInfo) {
        when(clientSessionCache.getClientSessionInfo(clientId)).thenReturn(clientSessionInfo);
    }

    private void mockClientSessionGetClientId(ClientSessionInfo clientSessionInfo, String clientId) {
        when(clientSessionInfo.getClientId()).thenReturn(clientId);
    }

    private Subscription newSubscription(int mqttQoSValue, String shareName) {
        return new Subscription(TOPIC, mqttQoSValue, clientSessionInfo, shareName, SubscriptionOptions.newInstance());
    }

    private ValueWithTopicFilter<ClientSubscription> newValueWithTopicFilter(String clientId, int qos, String topic) {
        return newValueWithTopicFilter(clientId, qos, null, topic);
    }

    private ValueWithTopicFilter<ClientSubscription> newValueWithTopicFilter(String clientId, int qos, String shareName, String topic) {
        return new ValueWithTopicFilter<>(newClientSubscription(clientId, qos, shareName), topic);
    }

    private ClientSubscription newClientSubscription(String clientId, int qos, String shareName) {
        return new ClientSubscription(clientId, qos, shareName, SubscriptionOptions.newInstance());
    }
}