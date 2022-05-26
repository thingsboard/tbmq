/**
 * Copyright © 2016-2020 The Thingsboard Authors
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

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttSubscribeMsg;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.service.security.authorization.AuthorizationRule;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttSubscribeHandlerTest {

    MqttMessageGenerator mqttMessageGenerator;
    ClientSubscriptionService clientSubscriptionService;
    TopicValidationService topicValidationService;
    AuthorizationRuleService authorizationRuleService;
    MqttSubscribeHandler mqttSubscribeHandler;

    ClientSessionCtx ctx;

    @BeforeEach
    void setUp() {
        mqttMessageGenerator = mock(MqttMessageGenerator.class);
        clientSubscriptionService = mock(ClientSubscriptionService.class);
        topicValidationService = mock(TopicValidationService.class);
        authorizationRuleService = mock(AuthorizationRuleService.class);
        mqttSubscribeHandler = spy(new MqttSubscribeHandler(mqttMessageGenerator, clientSubscriptionService, topicValidationService, authorizationRuleService));

        ctx = mock(ClientSessionCtx.class);
        when(ctx.getAuthorizationRules()).thenReturn(List.of(new AuthorizationRule(Collections.emptyList())));
        ChannelHandlerContext handlerContext = mock(ChannelHandlerContext.class);
        when(ctx.getChannel()).thenReturn(handlerContext);
    }

    @Test
    void testProcess() {
        SessionInfo sessionInfo = mock(SessionInfo.class);
        when(ctx.getSessionInfo()).thenReturn(sessionInfo);
        ClientInfo clientInfo = mock(ClientInfo.class);
        when(sessionInfo.getClientInfo()).thenReturn(clientInfo);

        when(authorizationRuleService.isAuthorized(any(), any())).thenReturn(true);

        MqttSubscribeMsg msg = new MqttSubscribeMsg(UUID.randomUUID(), 1, getTopicSubscriptions());
        mqttSubscribeHandler.process(ctx, msg);

        verify(mqttMessageGenerator, times(1)).createSubAckMessage(eq(1), eq(List.of(1, 1)));
        verify(clientSubscriptionService, times(1)).subscribeAndPersist(any(), any(), any());
    }

    @Test
    void testValidateSubscriptionsFailure() {
        doThrow(DataValidationException.class).when(topicValidationService).validateTopicFilter(any());
        assertThrows(MqttException.class,
                () -> mqttSubscribeHandler.validateSubscriptions("id", UUID.randomUUID(), List.of(getTopicSubscription("topic"))));
        verify(topicValidationService, times(1)).validateTopicFilter(any());
    }

    @Test
    void testValidateSubscriptionsSuccess() {
        doNothing().when(topicValidationService).validateTopicFilter(any());
        assertAll(
                () -> mqttSubscribeHandler.validateSubscriptions(
                        "id",
                        UUID.randomUUID(),
                        getTopicSubscriptions()));
        verify(topicValidationService, times(2)).validateTopicFilter(any());
    }

    @Test
    void testValidateClientAccessFailure() {
        assertThrows(MqttException.class, () -> mqttSubscribeHandler.validateClientAccess(ctx, List.of(getTopicSubscription("topic"))));
        verify(authorizationRuleService, times(1)).isAuthorized(any(), any());
    }

    @Test
    void testValidateClientAccessSuccess() {
        when(authorizationRuleService.isAuthorized(any(), any())).thenReturn(true);
        assertAll(() -> mqttSubscribeHandler.validateClientAccess(
                ctx,
                getTopicSubscriptions()
        ));
        verify(authorizationRuleService, times(2)).isAuthorized(any(), any());
    }

    private List<TopicSubscription> getTopicSubscriptions() {
        return List.of(
                getTopicSubscription("topic1"),
                getTopicSubscription("topic2")
        );
    }

    private TopicSubscription getTopicSubscription(String topic) {
        return new TopicSubscription(topic, 1);
    }
}