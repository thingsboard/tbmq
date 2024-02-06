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
package org.thingsboard.mqtt.broker.actors.client.service.handlers;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.dao.topic.TopicValidationService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = MqttSubscribeHandler.class)
public class MqttSubscribeHandlerTest {

    @MockBean
    MqttMessageGenerator mqttMessageGenerator;
    @MockBean
    ClientSubscriptionService clientSubscriptionService;
    @MockBean
    TopicValidationService topicValidationService;
    @MockBean
    AuthorizationRuleService authorizationRuleService;
    @MockBean
    RetainedMsgService retainedMsgService;
    @MockBean
    PublishMsgDeliveryService publishMsgDeliveryService;
    @MockBean
    ClientMqttActorManager clientMqttActorManager;
    @MockBean
    ApplicationSharedSubscriptionService applicationSharedSubscriptionService;
    @MockBean
    MsgPersistenceManager msgPersistenceManager;
    @MockBean
    ApplicationPersistenceProcessor applicationPersistenceProcessor;
    @SpyBean
    MqttSubscribeHandler mqttSubscribeHandler;

    ClientSessionCtx ctx;

    @Before
    public void setUp() {
        ctx = mock(ClientSessionCtx.class);
        when(ctx.getAuthRulePatterns()).thenReturn(List.of(AuthRulePatterns.newInstance(Collections.emptyList())));
    }

    @After
    public void tearDown() {
        Mockito.reset(ctx);
    }

    @Test
    public void givenTopicSubscription_whenCheckIsSharedSubscriptionWithNoLocal_thenReturnExpectedResult() {
        boolean result = mqttSubscribeHandler.isSharedSubscriptionWithNoLocal(
                new TopicSubscription("qwe", 1, "g1"));
        assertFalse(result);

        result = mqttSubscribeHandler.isSharedSubscriptionWithNoLocal(
                new TopicSubscription(
                        "qwe",
                        1,
                        null,
                        new SubscriptionOptions(
                                true,
                                true,
                                SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE)));
        assertFalse(result);

        result = mqttSubscribeHandler.isSharedSubscriptionWithNoLocal(
                new TopicSubscription(
                        "qwe",
                        1,
                        null,
                        new SubscriptionOptions(
                                false,
                                true,
                                SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE)));
        assertFalse(result);

        result = mqttSubscribeHandler.isSharedSubscriptionWithNoLocal(
                new TopicSubscription(
                        "qwe",
                        1,
                        "g1",
                        new SubscriptionOptions(
                                true,
                                false,
                                SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE)));
        assertTrue(result);
    }

    @Test
    public void givenTopicSubscription_whenValidateSharedSubscription_thenSuccess() {
        mqttSubscribeHandler.validateSharedSubscription(new TopicSubscription("tf", 1, "g"));
    }

    @Test(expected = DataValidationException.class)
    public void givenTopicSubscription_whenValidateSharedSubscriptionWithEmptyShareName_thenFailure() {
        mqttSubscribeHandler.validateSharedSubscription(new TopicSubscription("tf", 1, ""));
    }

    @Test(expected = DataValidationException.class)
    public void givenTopicSubscription_whenValidateSharedSubscriptionWithWildcardShareName_thenFailure() {
        mqttSubscribeHandler.validateSharedSubscription(new TopicSubscription("tf", 1, "#"));
    }

    @Test
    public void givenTopicSubscriptions_whenFilterSameSubscriptionsWithDifferentQos_thenGetExpectedResult() {
        BiFunction<TopicSharedSubscription, TopicSharedSubscription, Boolean> f =
                (currentSubscription, newSubscription) -> currentSubscription != null && currentSubscription.getQos() != newSubscription.getQos();
        Set<TopicSharedSubscription> newSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 2),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s3", 2),
                new TopicSharedSubscription("tf44", "s44", 1)
        );
        Set<TopicSharedSubscription> currentSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 1),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s33", 2),
                new TopicSharedSubscription("tf4", "s44", 1)
        );
        Set<TopicSharedSubscription> result = mqttSubscribeHandler.filterSameSubscriptionsWithDifferentQos(newSharedSubscriptions, currentSharedSubscriptions, f);
        assertTrue(result.contains(new TopicSharedSubscription("tf1", "s1", 2)));
    }

    @Test
    public void givenCurrentAndNewSubscriptions_whenCheckIsQosDifferent_thenTrue() {
        boolean result = mqttSubscribeHandler.isQosDifferent(
                new TopicSharedSubscription("tf", "#", 1),
                new TopicSharedSubscription("tf", "#", 2));
        assertTrue(result);
    }

    @Test
    public void givenCurrentAndNewSubscriptions_whenCheckIsQosDifferent_thenFalse() {
        boolean result = mqttSubscribeHandler.isQosDifferent(
                null,
                new TopicSharedSubscription("tf", "#", 2));
        assertFalse(result);

        result = mqttSubscribeHandler.isQosDifferent(
                new TopicSharedSubscription("tf", "#", 2),
                new TopicSharedSubscription("tf", "#", 2));
        assertFalse(result);
    }

    @Test
    public void givenCurrentAndNewSubscriptions_whenCheckIsQosDifferentAndCurrentIsZero_thenTrue() {
        boolean result = mqttSubscribeHandler.isQosDifferentAndCurrentIsZero(
                new TopicSharedSubscription("tf", "#", 0),
                new TopicSharedSubscription("tf", "#", 2));
        assertTrue(result);
    }

    @Test
    public void givenCurrentAndNewSubscriptions_whenCheckIsQosDifferentAndCurrentIsZero_thenFalse() {
        boolean result = mqttSubscribeHandler.isQosDifferentAndCurrentIsZero(
                new TopicSharedSubscription("tf", "#", 0),
                new TopicSharedSubscription("tf", "#", 0));
        assertFalse(result);

        result = mqttSubscribeHandler.isQosDifferentAndCurrentIsZero(
                new TopicSharedSubscription("tf", "#", 1),
                new TopicSharedSubscription("tf", "#", 2));
        assertFalse(result);

        result = mqttSubscribeHandler.isQosDifferentAndCurrentIsZero(
                new TopicSharedSubscription("tf", "#", 1),
                new TopicSharedSubscription("tf", "#", 0));
        assertFalse(result);
    }

    @Test
    public void givenCurrentAndNewSubscriptions_whenFindSameSubscriptionsWithDifferentQosForApplication_thenReturnExpectedResult() {
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        doNothing().when(applicationPersistenceProcessor).stopProcessingSharedSubscriptions(any(), anySet());

        Set<TopicSharedSubscription> newSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 2),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s3", 2),
                new TopicSharedSubscription("tf44", "s44", 1)
        );
        Set<TopicSharedSubscription> currentSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 1),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s33", 2),
                new TopicSharedSubscription("tf4", "s44", 1)
        );

        Set<TopicSharedSubscription> result = mqttSubscribeHandler.findSameSubscriptionsWithDifferentQos(ctx, newSharedSubscriptions, currentSharedSubscriptions);
        TopicSharedSubscription resultSubscription = new TopicSharedSubscription("tf1", "s1", 2);
        assertTrue(result.contains(resultSubscription));
        verify(applicationPersistenceProcessor, times(1)).stopProcessingSharedSubscriptions(eq(ctx), eq(Set.of(resultSubscription)));
    }

    @Test
    public void givenCurrentAndNewSubscriptions_whenFindSameSubscriptionsWithDifferentQosForDeviceWithQosZero_thenReturnEmptyResult() {
        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);

        Set<TopicSharedSubscription> newSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 0),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s3", 2),
                new TopicSharedSubscription("tf44", "s44", 1)
        );
        Set<TopicSharedSubscription> currentSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 1),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s33", 2),
                new TopicSharedSubscription("tf4", "s44", 1)
        );

        Set<TopicSharedSubscription> result = mqttSubscribeHandler.findSameSubscriptionsWithDifferentQos(ctx, newSharedSubscriptions, currentSharedSubscriptions);
        assertTrue(result.isEmpty());
    }

    @Test
    public void givenCurrentAndNewSubscriptions_whenFindSameSubscriptionsWithDifferentQosForDevice_thenReturnExpectedResult() {
        when(ctx.getClientType()).thenReturn(ClientType.DEVICE);

        Set<TopicSharedSubscription> newSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 2),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s3", 2),
                new TopicSharedSubscription("tf44", "s44", 1)
        );
        Set<TopicSharedSubscription> currentSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 0),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s33", 2),
                new TopicSharedSubscription("tf4", "s44", 1)
        );

        Set<TopicSharedSubscription> result = mqttSubscribeHandler.findSameSubscriptionsWithDifferentQos(ctx, newSharedSubscriptions, currentSharedSubscriptions);
        assertTrue(result.contains(new TopicSharedSubscription("tf1", "s1", 2)));
    }

    @Test
    public void givenCurrentAndNewSubscriptions_whenFilterNewUniqueSubscriptions_thenReturnExpectedResult() {
        Set<TopicSharedSubscription> newSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 2),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s3", 2),
                new TopicSharedSubscription("tf44", "s44", 1)
        );
        Set<TopicSharedSubscription> currentSharedSubscriptions = Set.of(
                new TopicSharedSubscription("tf1", "s1", 0),
                new TopicSharedSubscription("tf2", "s2", 0),
                new TopicSharedSubscription("tf3", "s33", 2),
                new TopicSharedSubscription("tf4", "s44", 1)
        );

        Set<TopicSharedSubscription> result = mqttSubscribeHandler.filterNewUniqueSubscriptions(newSharedSubscriptions, currentSharedSubscriptions);
        assertTrue(result.containsAll(
                Set.of(
                        new TopicSharedSubscription("tf3", "s3", 2),
                        new TopicSharedSubscription("tf44", "s44", 1)
                )
        ));
    }

    @Test
    public void givenTopicSubscription_whenGetRetainedMessagesForTopicSubscription_thenReturnEmptyResult() {
        when(retainedMsgService.getRetainedMessages("tf")).thenReturn(List.of());
        List<RetainedMsg> messages = mqttSubscribeHandler.getRetainedMessagesForTopicSubscription(new TopicSubscription("tf", 1));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void givenTopicSubscription_whenGetRetainedMessagesForTopicSubscription_thenReturnExpectedResult() {
        MqttProperties properties = new MqttProperties();

        when(retainedMsgService.getRetainedMessages("tf")).thenReturn(List.of(new RetainedMsg("tf", null, 1, properties)));
        List<RetainedMsg> messages = mqttSubscribeHandler.getRetainedMessagesForTopicSubscription(new TopicSubscription("tf", 0));
        assertEquals(1, messages.size());
        assertEquals(0, messages.get(0).getQosLevel());
    }

    @Test
    public void givenTopicSubscription_whenGetExpiredRetainedMessagesForTopicSubscription_thenReturnEmptyResult() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, -10));

        when(retainedMsgService.getRetainedMessages("tf")).thenReturn(List.of(new RetainedMsg("tf", null, 1, properties)));
        List<RetainedMsg> messages = mqttSubscribeHandler.getRetainedMessagesForTopicSubscription(new TopicSubscription("tf", 0));
        assertTrue(messages.isEmpty());
    }

    @Test
    public void givenTopicSubscription_whenGetNotExpiredRetainedMessagesForTopicSubscription_thenReturnExpectedResult() {
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.PUB_EXPIRY_INTERVAL_PROP_ID, 30));

        when(retainedMsgService.getRetainedMessages("tf")).thenReturn(List.of(new RetainedMsg("tf", null, 1, properties)));
        List<RetainedMsg> messages = mqttSubscribeHandler.getRetainedMessagesForTopicSubscription(new TopicSubscription("tf", 0));
        assertEquals(1, messages.size());
    }

    @Test
    public void givenMqttSubscribeMsg_whenProcessSubscriptions_thenReturnExpectedResult() {
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        ClientInfo clientInfo = mock(ClientInfo.class);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);

        when(authorizationRuleService.isSubAuthorized(any(), any())).thenReturn(true);

        MqttSubscribeMsg msg = new MqttSubscribeMsg(UUID.randomUUID(), 1, getTopicSubscriptions());
        mqttSubscribeHandler.process(ctx, msg);

        verify(mqttMessageGenerator, times(1)).createSubAckMessage(
                eq(1), eq(List.of(MqttReasonCodes.SubAck.GRANTED_QOS_0, MqttReasonCodes.SubAck.GRANTED_QOS_1, MqttReasonCodes.SubAck.GRANTED_QOS_2))
        );
        verify(clientSubscriptionService, times(1)).subscribeAndPersist(any(), any(), any());
        verify(clientSubscriptionService, times(1)).getClientSharedSubscriptions(any());
    }

    @Test
    public void givenMqttSubscribeMsg_whenCollectMqttReasonCodes_thenReturnExpectedResult() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        doThrow(DataValidationException.class).when(topicValidationService).validateTopicFilter(eq("topic1"));
        when(authorizationRuleService.isSubAuthorized(eq("topic2"), any())).thenReturn(false);
        when(authorizationRuleService.isSubAuthorized(eq("topic3"), any())).thenReturn(true);

        List<TopicSubscription> topicSubscriptions = getTopicSubscriptions();
        MqttSubscribeMsg msg = new MqttSubscribeMsg(UUID.randomUUID(), 1, topicSubscriptions);
        List<MqttReasonCodes.SubAck> reasonCodes = mqttSubscribeHandler.collectMqttReasonCodes(ctx, msg);

        assertEquals(List.of(MqttReasonCodes.SubAck.UNSPECIFIED_ERROR, MqttReasonCodes.SubAck.NOT_AUTHORIZED, MqttReasonCodes.SubAck.GRANTED_QOS_2), reasonCodes);
    }

    @Test
    public void givenMqttSubscribeMsgWithApplicationSharedSubscriptionAndNoEntityCreated_whenCollectMqttReasonCodes_thenReturnExpectedResult() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        when(authorizationRuleService.isSubAuthorized(eq("tf1"), any())).thenReturn(true);
        SessionInfo sessionInfo = SessionInfo.builder().sessionExpiryInterval(123000).cleanStart(false).build();
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        when(applicationSharedSubscriptionService.findSharedSubscriptionByTopic("tf1")).thenReturn(null);

        List<TopicSubscription> topicSubscriptions = List.of(new TopicSubscription("tf1", 1, "g1"));
        MqttSubscribeMsg msg = new MqttSubscribeMsg(UUID.randomUUID(), 1, topicSubscriptions);
        List<MqttReasonCodes.SubAck> reasonCodes = mqttSubscribeHandler.collectMqttReasonCodes(ctx, msg);

        assertEquals(List.of(MqttReasonCodes.SubAck.IMPLEMENTATION_SPECIFIC_ERROR), reasonCodes);
    }

    @Test
    public void givenMqttSubscribeMsgWithApplicationSharedSubscriptionAndEntityCreated_whenCollectMqttReasonCodes_thenReturnExpectedResult() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        when(authorizationRuleService.isSubAuthorized(eq("tf1"), any())).thenReturn(true);
        SessionInfo sessionInfo = SessionInfo.builder().sessionExpiryInterval(123000).cleanStart(false).build();
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        when(ctx.getClientType()).thenReturn(ClientType.APPLICATION);
        when(applicationSharedSubscriptionService.findSharedSubscriptionByTopic("tf1")).thenReturn(new ApplicationSharedSubscription());

        List<TopicSubscription> topicSubscriptions = List.of(new TopicSubscription("tf1", 1, "g1"));
        MqttSubscribeMsg msg = new MqttSubscribeMsg(UUID.randomUUID(), 1, topicSubscriptions);
        List<MqttReasonCodes.SubAck> reasonCodes = mqttSubscribeHandler.collectMqttReasonCodes(ctx, msg);

        assertEquals(List.of(MqttReasonCodes.SubAck.GRANTED_QOS_1), reasonCodes);
    }

    @Test
    public void givenMqttSubscribeMsgWithSharedSubscriptionAndNoLocal_whenCollectMqttReasonCodes_thenReturnEmptyResult() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        List<TopicSubscription> topicSubscriptions = List.of(new TopicSubscription("tf", 1, "g",
                new SubscriptionOptions(true, true, SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE)));
        MqttSubscribeMsg msg = new MqttSubscribeMsg(UUID.randomUUID(), 1, topicSubscriptions);
        List<MqttReasonCodes.SubAck> reasonCodes = mqttSubscribeHandler.collectMqttReasonCodes(ctx, msg);

        assertTrue(reasonCodes.isEmpty());
        verify(clientMqttActorManager, times(1)).disconnect(any(), any());
    }

    @Test
    public void givenMqttSubscribeMsgWithSubscriptionIdentifier_whenCollectMqttReasonCodes_thenReturnSubscriptionIdNotSupported() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        MqttSubscribeMsg msg = getMqttSubscribeMsg();
        List<MqttReasonCodes.SubAck> reasonCodes = mqttSubscribeHandler.collectMqttReasonCodes(ctx, msg);

        assertEquals(
                List.of(
                        MqttReasonCodes.SubAck.SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED,
                        MqttReasonCodes.SubAck.SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED,
                        MqttReasonCodes.SubAck.SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED),
                reasonCodes);
    }

    private MqttSubscribeMsg getMqttSubscribeMsg() {
        List<TopicSubscription> topicSubscriptions = getTopicSubscriptions();
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(BrokerConstants.SUBSCRIPTION_IDENTIFIER_PROP_ID, 1));
        return new MqttSubscribeMsg(UUID.randomUUID(), 1, topicSubscriptions, properties);
    }

    @Test
    public void givenNewAndCurrentSubscriptions_whenGetRetainedMessagesForTopicSubscriptions_thenReturnExpectedResult() {
        long ts = System.currentTimeMillis();
        when(retainedMsgService.getRetainedMessages(eq("one"))).thenReturn(List.of(
                newRetainedMsg("payload1", 1, ts), newRetainedMsg("payload2", 1, ts)
        ));
        when(retainedMsgService.getRetainedMessages(eq("two"))).thenReturn(List.of(
                newRetainedMsg("payload3", 0, ts), newRetainedMsg("payload4", 0, ts)
        ));
        when(retainedMsgService.getRetainedMessages(eq("three"))).thenReturn(List.of(
                newRetainedMsg("payload5", 2, ts)
        ));
        when(retainedMsgService.getRetainedMessages(eq("four"))).thenReturn(List.of(
                newRetainedMsg("payload6", 1, ts)
        ));
        when(retainedMsgService.getRetainedMessages(eq("five"))).thenReturn(List.of(
                newRetainedMsg("payload7", 2, ts)
        ));

        Set<RetainedMsg> retainedMsgSet = mqttSubscribeHandler.getRetainedMessagesForTopicSubscriptions(
                List.of(
                        getTopicSubscription("one", 1),
                        getTopicSubscription("two", 2),
                        getTopicSubscription("three", 1),
                        getTopicSubscription("four", 1),
                        getTopicSubscription("five", 0)
                ), Collections.emptySet()
        );
        assertEquals(7, retainedMsgSet.size());
    }

    @Test
    public void givenNewAndCurrentSubscriptions_whenGetRetainedMessagesForTopicSubscriptionsWithOptions_thenReturnExpectedResult() {
        long ts = System.currentTimeMillis();
        when(retainedMsgService.getRetainedMessages(eq("one"))).thenReturn(List.of(
                newRetainedMsg("payload1", 1, ts), newRetainedMsg("payload2", 1, ts)
        ));
        when(retainedMsgService.getRetainedMessages(eq("two"))).thenReturn(List.of(
                newRetainedMsg("payload3", 0, ts), newRetainedMsg("payload4", 0, ts)
        ));
        when(retainedMsgService.getRetainedMessages(eq("three"))).thenReturn(List.of(
                newRetainedMsg("payload5", 2, ts), newRetainedMsg("payload5", 2, ts)
        ));
        when(retainedMsgService.getRetainedMessages(eq("four"))).thenReturn(List.of(
                newRetainedMsg("payload6", 1, ts), newRetainedMsg("payload1", 1, ts)
        ));
        when(retainedMsgService.getRetainedMessages(eq("five"))).thenReturn(List.of(
                newRetainedMsg("payload6", 2, ts), newRetainedMsg("payload4", 1, ts)
        ));

        Set<RetainedMsg> retainedMsgSet = mqttSubscribeHandler.getRetainedMessagesForTopicSubscriptions(
                List.of(
                        getTopicSubscription("one", 1, getOptions(SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE)),
                        getTopicSubscription("two", 2, getOptions(SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS)),
                        getTopicSubscription("three", 1, getOptions(SubscriptionOptions.RetainHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS)),
                        getTopicSubscription("four", 1, getOptions(SubscriptionOptions.RetainHandlingPolicy.DONT_SEND_AT_SUBSCRIBE)),
                        getTopicSubscription("five", 0, getOptions(SubscriptionOptions.RetainHandlingPolicy.DONT_SEND_AT_SUBSCRIBE))
                ),
                Set.of(getTopicSubscription("two", 1))
        );
        assertEquals(3, retainedMsgSet.size());
    }

    private static SubscriptionOptions getOptions(SubscriptionOptions.RetainHandlingPolicy retainHandlingPolicy) {
        return new SubscriptionOptions(false, false, retainHandlingPolicy);
    }

    @Test
    public void givenTopicSubscriptions_whenCollectUniqueSharedSubscriptions_thenGetExpectedSize() {
        List<TopicSubscription> topicSubscriptions = List.of(
                getTopicSubscription("topic/test1", 1),
                getTopicSubscription("topic/test2", 1, "g1"),
                getTopicSubscription("topic/test2", 1, "g2"),
                getTopicSubscription("topic/test3", 1, "g1"),
                getTopicSubscription("topic/test3", 2, "g1"),
                getTopicSubscription("topic/+", 1, "g1"),
                getTopicSubscription("topic/#", 1, "g2")
        );
        Set<TopicSharedSubscription> result = mqttSubscribeHandler.collectUniqueSharedSubscriptions(topicSubscriptions);
        assertEquals(5, result.size());
    }

    private List<TopicSubscription> getTopicSubscriptions() {
        return List.of(
                getTopicSubscription("topic1", 0),
                getTopicSubscription("topic2", 1),
                getTopicSubscription("topic3", 2)
        );
    }

    private TopicSubscription getTopicSubscription(String topic, int qos) {
        String shareName = null;
        return getTopicSubscription(topic, qos, shareName);
    }

    private TopicSubscription getTopicSubscription(String topic, int qos, String shareName) {
        return new TopicSubscription(topic, qos, shareName);
    }

    private TopicSubscription getTopicSubscription(String topic, int qos, SubscriptionOptions options) {
        return new TopicSubscription(topic, qos, options);
    }

    private RetainedMsg newRetainedMsg(String payload, int qos, long ts) {
        return new RetainedMsg("#", payload.getBytes(StandardCharsets.UTF_8), qos, MqttProperties.NO_PROPERTIES, ts);
    }
}
