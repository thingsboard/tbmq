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
import io.netty.handler.codec.mqtt.MqttVersion;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgService;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthRulePatterns;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.util.MqttReasonCode;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @SpyBean
    MqttSubscribeHandler mqttSubscribeHandler;

    ClientSessionCtx ctx;

    @Before
    public void setUp() {
        ctx = mock(ClientSessionCtx.class);
        when(ctx.getAuthRulePatterns()).thenReturn(List.of(AuthRulePatterns.newInstance(Collections.emptyList())));
    }

    @Test
    public void testProcess() {
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        ClientInfo clientInfo = mock(ClientInfo.class);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);

        when(authorizationRuleService.isSubAuthorized(any(), any())).thenReturn(true);

        MqttSubscribeMsg msg = new MqttSubscribeMsg(UUID.randomUUID(), 1, getTopicSubscriptions());
        mqttSubscribeHandler.process(ctx, msg);

        verify(mqttMessageGenerator, times(1)).createSubAckMessage(
                eq(1), eq(List.of(MqttReasonCode.GRANTED_QOS_0, MqttReasonCode.GRANTED_QOS_1, MqttReasonCode.GRANTED_QOS_2))
        );
        verify(clientSubscriptionService, times(1)).subscribeAndPersist(any(), any(), any());
    }

    @Test
    public void testCollectMqttReasonCodes1() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);
        doThrow(DataValidationException.class).when(topicValidationService).validateTopicFilter(eq("topic1"));
        when(authorizationRuleService.isSubAuthorized(eq("topic2"), any())).thenReturn(false);
        when(authorizationRuleService.isSubAuthorized(eq("topic3"), any())).thenReturn(true);

        List<TopicSubscription> topicSubscriptions = getTopicSubscriptions();
        MqttSubscribeMsg msg = new MqttSubscribeMsg(UUID.randomUUID(), 1, topicSubscriptions);
        List<MqttReasonCode> reasonCodes = mqttSubscribeHandler.collectMqttReasonCodes(ctx, msg);

        assertEquals(List.of(MqttReasonCode.FAILURE, MqttReasonCode.NOT_AUTHORIZED, MqttReasonCode.GRANTED_QOS_2), reasonCodes);
    }

    @Test
    public void testCollectMqttReasonCodes2() {
        when(ctx.getMqttVersion()).thenReturn(MqttVersion.MQTT_5);

        MqttSubscribeMsg msg = getMqttSubscribeMsg();
        List<MqttReasonCode> reasonCodes = mqttSubscribeHandler.collectMqttReasonCodes(ctx, msg);

        assertEquals(
                List.of(
                        MqttReasonCode.SUBSCRIPTION_ID_NOT_SUPPORTED,
                        MqttReasonCode.SUBSCRIPTION_ID_NOT_SUPPORTED,
                        MqttReasonCode.SUBSCRIPTION_ID_NOT_SUPPORTED),
                reasonCodes);
    }

    private MqttSubscribeMsg getMqttSubscribeMsg() {
        List<TopicSubscription> topicSubscriptions = getTopicSubscriptions();
        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value(), 1));
        return new MqttSubscribeMsg(UUID.randomUUID(), 1, topicSubscriptions, properties);
    }

    @Test
    public void testGetRetainedMsgsForTopics() {
        when(retainedMsgService.getRetainedMessages(eq("one"))).thenReturn(List.of(
                newRetainedMsg("payload1", 1), newRetainedMsg("payload2", 1)
        ));
        when(retainedMsgService.getRetainedMessages(eq("two"))).thenReturn(List.of(
                newRetainedMsg("payload3", 0), newRetainedMsg("payload4", 0)
        ));
        when(retainedMsgService.getRetainedMessages(eq("three"))).thenReturn(List.of(
                newRetainedMsg("payload5", 2)
        ));
        when(retainedMsgService.getRetainedMessages(eq("four"))).thenReturn(List.of(
                newRetainedMsg("payload6", 1)
        ));
        when(retainedMsgService.getRetainedMessages(eq("five"))).thenReturn(List.of(
                newRetainedMsg("payload7", 2)
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
    public void testGetRetainedMsgsForTopicsWithOptions() {
        when(retainedMsgService.getRetainedMessages(eq("one"))).thenReturn(List.of(
                newRetainedMsg("payload1", 1), newRetainedMsg("payload2", 1)
        ));
        when(retainedMsgService.getRetainedMessages(eq("two"))).thenReturn(List.of(
                newRetainedMsg("payload3", 0), newRetainedMsg("payload4", 0)
        ));
        when(retainedMsgService.getRetainedMessages(eq("three"))).thenReturn(List.of(
                newRetainedMsg("payload5", 2), newRetainedMsg("payload5", 2)
        ));
        when(retainedMsgService.getRetainedMessages(eq("four"))).thenReturn(List.of(
                newRetainedMsg("payload6", 1), newRetainedMsg("payload1", 1)
        ));
        when(retainedMsgService.getRetainedMessages(eq("five"))).thenReturn(List.of(
                newRetainedMsg("payload6", 2), newRetainedMsg("payload4", 1)
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
    public void testCollectUniqueSharedSubscriptions() {
        List<TopicSubscription> topicSubscriptions = List.of(
                getTopicSubscription("topic/test1", 1),
                getTopicSubscription("topic/test2", 1, "g1"),
                getTopicSubscription("topic/test2", 1, "g2"),
                getTopicSubscription("topic/test3", 1, "g1"),
                getTopicSubscription("topic/test3", 2, "g1"),
                getTopicSubscription("topic/+", 1, "g1"),
                getTopicSubscription("topic/#", 1, "g2")
        );
        Set<TopicSharedSubscription> test = mqttSubscribeHandler.collectUniqueSharedSubscriptions(topicSubscriptions);
        assertEquals(5, test.size());
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

    private RetainedMsg newRetainedMsg(String payload, int qos) {
        return new RetainedMsg("#", payload.getBytes(StandardCharsets.UTF_8), qos);
    }
}