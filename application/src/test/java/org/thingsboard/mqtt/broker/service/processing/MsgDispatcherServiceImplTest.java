/**
 * Copyright © 2016-2025 The Thingsboard Authors
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

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.processing.data.MsgSubscriptions;
import org.thingsboard.mqtt.broker.service.processing.data.PersistentMsgSubscriptions;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;
import org.thingsboard.mqtt.broker.service.processing.shared.DeviceSharedSubscriptionProcessorImpl;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.EntitySubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionCacheServiceImpl;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptions;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MsgDispatcherServiceImpl.class)
public class MsgDispatcherServiceImplTest {

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
    DeviceSharedSubscriptionProcessorImpl deviceSharedSubscriptionProcessor;
    @MockBean
    SharedSubscriptionCacheServiceImpl sharedSubscriptionCacheService;
    @MockBean
    TbMessageStatsReportClient tbMessageStatsReportClient;
    @MockBean
    RateLimitService rateLimitService;
    @SpyBean
    MsgDispatcherServiceImpl msgDispatcherService;

    ClientSessionInfo clientSessionInfo;

    @Before
    public void setUp() {
        clientSessionInfo = mock(ClientSessionInfo.class);

        when(rateLimitService.isTotalMsgsLimitEnabled()).thenReturn(false);
    }

    @Test
    public void testApplyTotalMsgsRateLimits_whenTotalMsgsLimitDisabled() {
        when(rateLimitService.isTotalMsgsLimitEnabled()).thenReturn(false);

        List<ValueWithTopicFilter<EntitySubscription>> list = List.of(
                newValueWithTopicFilter("c1", 0, "t1"),
                newValueWithTopicFilter("c2", 1, "t2"),
                newValueWithTopicFilter("c3", 2, "t3")
        );
        List<ValueWithTopicFilter<EntitySubscription>> result = msgDispatcherService.applyTotalMsgsRateLimits(list);

        assertEquals(list, result);
    }

    @Test
    public void testApplyTotalMsgsRateLimits_whenTotalMsgsLimitEnabledAndLimitReached() {
        when(rateLimitService.isTotalMsgsLimitEnabled()).thenReturn(true);
        when(rateLimitService.tryConsumeAsMuchAsPossibleTotalMsgs(eq(3L))).thenReturn(0L);

        List<ValueWithTopicFilter<EntitySubscription>> list = List.of(
                newValueWithTopicFilter("c1", 0, "t1"),
                newValueWithTopicFilter("c2", 1, "t2"),
                newValueWithTopicFilter("c3", 2, "t3")
        );
        List<ValueWithTopicFilter<EntitySubscription>> result = msgDispatcherService.applyTotalMsgsRateLimits(list);

        assertEquals(List.of(), result);
    }

    @Test
    public void testApplyTotalMsgsRateLimits_whenTotalMsgsLimitEnabledAndLimitNotUsed() {
        // the limit is set to (size - 1) since one token
        // was already consumed on retrieval from 'tbmq.msg.all' queue.
        when(rateLimitService.isTotalMsgsLimitEnabled()).thenReturn(true);
        when(rateLimitService.tryConsumeAsMuchAsPossibleTotalMsgs(eq(2L))).thenReturn(2L);

        List<ValueWithTopicFilter<EntitySubscription>> list = List.of(
                newValueWithTopicFilter("c1", 0, "t1"),
                newValueWithTopicFilter("c2", 1, "t2"),
                newValueWithTopicFilter("c3", 2, "t3")
        );
        List<ValueWithTopicFilter<EntitySubscription>> result = msgDispatcherService.applyTotalMsgsRateLimits(list);

        assertEquals(list, result);
    }

    @Test
    public void testApplyTotalMsgsRateLimits_whenTotalMsgsLimitEnabledAndLimitNotReached() {
        when(rateLimitService.isTotalMsgsLimitEnabled()).thenReturn(true);
        when(rateLimitService.tryConsumeAsMuchAsPossibleTotalMsgs(eq(4L))).thenReturn(1L);

        List<ValueWithTopicFilter<EntitySubscription>> list = List.of(
                newValueWithTopicFilter("c1", 0, "t1"),
                newValueWithTopicFilter("c2", 1, "t2"),
                newValueWithTopicFilter("c3", 2, "t3"),
                newValueWithTopicFilter("c4", 0, "t4"),
                newValueWithTopicFilter("c5", 1, "t5")
        );
        List<ValueWithTopicFilter<EntitySubscription>> result = msgDispatcherService.applyTotalMsgsRateLimits(list);

        assertEquals(2, result.size());
        assertEquals("t1", result.get(0).getTopicFilter());
        assertEquals("c1", result.get(0).getValue().getClientId());
        assertEquals("t2", result.get(1).getTopicFilter());
        assertEquals("c2", result.get(1).getValue().getClientId());
    }

    @Test
    public void testGetSubscriptionWithHigherQos() {
        Subscription subscription1 = new Subscription("test/+", 1, (ClientSessionInfo) null);
        Subscription subscription2 = new Subscription("#", 0, (ClientSessionInfo) null);

        Subscription result = msgDispatcherService.getSubscriptionWithHigherQosAndAllSubscriptionIds(subscription1, subscription2);

        assertEquals(result, subscription1);
    }

    @Test
    public void testGetSubscriptionWithHigherQosAndAllSubscriptionIds() {
        Subscription subscription1 = new Subscription("test/+", 1, null, null, null, Lists.newArrayList(1, 2));
        Subscription subscription2 = new Subscription("#", 0, null, null, null, Lists.newArrayList(3, 4, 1));

        Subscription result = msgDispatcherService.getSubscriptionWithHigherQosAndAllSubscriptionIds(subscription1, subscription2);
        assertEquals(1, result.getQos());
        assertEquals(List.of(1, 2, 3, 4, 1), result.getSubscriptionIds());
    }

    @Test
    public void testCollectSubscriptions4() {
        List<ValueWithTopicFilter<EntitySubscription>> before = List.of(
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

        List<Subscription> result = msgDispatcherService.collectSubscriptions(before, "clientId1");
        assertNull(result);
    }

    @Test
    public void testCollectSubscriptions3() {
        when(clientSessionCache.getClientSessionInfo("clientId1")).thenReturn(ClientSessionInfo.builder().clientId("clientId1").build());

        List<ValueWithTopicFilter<EntitySubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 0, "+/test/+")
        );
        assertEquals(1, before.size());

        List<Subscription> result = msgDispatcherService.collectSubscriptions(before, null);
        assertEquals(1, result.size());
    }

    @Test
    public void testCollectSubscriptions2() {
        for (int i = 1; i < 7; i++) {
            when(clientSessionCache.getClientSessionInfo("clientId" + i)).thenReturn(ClientSessionInfo.builder().clientId("clientId" + i).build());
        }

        List<ValueWithTopicFilter<EntitySubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 0, "+/test/+"),
                newValueWithTopicFilter("clientId2", 0, "#"),
                newValueWithTopicFilter("clientId3", 2, "topic/+/+"),
                newValueWithTopicFilter("clientId1", 2, "+/+/res"),
                newValueWithTopicFilter("clientId2", 1, "topic/test/+"),
                newValueWithTopicFilter("clientId3", 0, "topic/+/res")
        );
        assertEquals(6, before.size());

        List<Subscription> result = msgDispatcherService.collectSubscriptions(before, null);
        assertEquals(3, result.size());

        result.sort(Comparator.comparing(Subscription::getClientId));

        assertEquals("clientId1", result.get(0).getClientId());
        assertEquals("clientId2", result.get(1).getClientId());
        assertEquals("clientId3", result.get(2).getClientId());

        assertEquals(2, result.get(0).getQos());
        assertEquals(1, result.get(1).getQos());
        assertEquals(2, result.get(2).getQos());
    }

    @Test
    public void testCollectSubscriptions1() {
        for (int i = 1; i < 7; i++) {
            when(clientSessionCache.getClientSessionInfo("clientId" + i)).thenReturn(ClientSessionInfo.builder().clientId("clientId" + i).build());
        }

        List<ValueWithTopicFilter<EntitySubscription>> before = List.of(
                newValueWithTopicFilter("clientId1", 1, "+/test/+"),
                newValueWithTopicFilter("clientId2", 1, "#"),
                newValueWithTopicFilter("clientId3", 1, "topic/+/+"),
                newValueWithTopicFilter("clientId4", 1, "+/+/res"),
                newValueWithTopicFilter("clientId5", 1, "topic/test/+"),
                newValueWithTopicFilter("clientId6", 1, "topic/+/res")
        );
        assertEquals(6, before.size());

        List<Subscription> result = msgDispatcherService.collectSubscriptions(before, null);
        assertEquals(6, result.size());
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
        PublishMsgProto publishMsgProto = PublishMsgProto
                .newBuilder()
                .setTopicName(topic)
                .build();

        when(sharedSubscriptionCacheService.sharedSubscriptionsInitialized()).thenReturn(true);
        when(deviceSharedSubscriptionProcessor.getTargetSubscriptions(anySet(), anyInt())).thenCallRealMethod();
        when(sharedSubscriptionCacheService.getSubscriptions(anyList())).thenCallRealMethod();

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
        MsgSubscriptions msgSubscriptions = new MsgSubscriptions();

        PublishMsgProto publishMsgProto = PublishMsgProto
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
        ClientSessionInfo clientSessionInfo6 = mock(ClientSessionInfo.class);
        ClientSessionInfo clientSessionInfo7 = mock(ClientSessionInfo.class);

        when(clientSessionInfo1.isPersistent()).thenReturn(true);
        when(clientSessionInfo2.isPersistent()).thenReturn(true);
        when(clientSessionInfo5.isPersistent()).thenReturn(false);
        when(clientSessionInfo6.isPersistent()).thenReturn(true);
        when(clientSessionInfo7.isPersistent()).thenReturn(true);

        when(clientSessionInfo1.getType()).thenReturn(ClientType.APPLICATION);
        when(clientSessionInfo2.getType()).thenReturn(ClientType.APPLICATION);
        when(clientSessionInfo5.getType()).thenReturn(ClientType.DEVICE);
        when(clientSessionInfo6.getType()).thenReturn(ClientType.DEVICE);
        when(clientSessionInfo7.getType()).thenReturn(ClientType.DEVICE);

        mockClientSessionGetClientId(clientSessionInfo1, "clientId1");
        mockClientSessionGetClientId(clientSessionInfo2, "clientId2");
        mockClientSessionGetClientId(clientSessionInfo6, "clientId6");
        mockClientSessionGetClientId(clientSessionInfo7, "clientId7");

        MsgSubscriptions msgSubscriptions = new MsgSubscriptions(
                List.of(
                        new Subscription("test/topic/1", 1, clientSessionInfo1),
                        new Subscription("test/+/1", 2, clientSessionInfo2),
                        new Subscription("test/+/1", 1, clientSessionInfo7)
                ),
                Set.of(
                        new Subscription("#", 2, clientSessionInfo3),
                        new Subscription("test/#", 0, clientSessionInfo4)
                ),
                List.of(
                        new Subscription("test/topic/#", 1, clientSessionInfo5),
                        new Subscription("+/topic/1", 2, clientSessionInfo6)
                )
        );
        PublishMsgProto publishMsgProto = PublishMsgProto
                .newBuilder()
                .setTopicName("topic/test")
                .setQos(2)
                .build();

        PersistentMsgSubscriptions persistentMsgSubscriptions =
                msgDispatcherService.processBasicAndCollectPersistentSubscriptions(msgSubscriptions, publishMsgProto);

        assertEquals(persistentMsgSubscriptions.getAllApplicationSharedSubscriptions(), msgSubscriptions.getAllApplicationSharedSubscriptions());

        assertEquals(2, persistentMsgSubscriptions.getDeviceSubscriptions().size());
        List<String> devClientIds = getClientIds(persistentMsgSubscriptions.getDeviceSubscriptions().stream());
        assertTrue(devClientIds.containsAll(List.of("clientId6", "clientId7")));

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

    private ValueWithTopicFilter<EntitySubscription> newValueWithTopicFilter(String clientId, int qos, String topic) {
        return newValueWithTopicFilter(clientId, qos, null, topic);
    }

    private ValueWithTopicFilter<EntitySubscription> newValueWithTopicFilter(String clientId, int qos, String shareName, String topic) {
        return new ValueWithTopicFilter<>(newClientSubscription(clientId, qos, shareName), topic);
    }

    private ClientSubscription newClientSubscription(String clientId, int qos, String shareName) {
        return new ClientSubscription(clientId, qos, shareName, SubscriptionOptions.newInstance());
    }
}
