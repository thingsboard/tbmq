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
package org.thingsboard.mqtt.broker.service.mqtt.validation;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.service.DefaultTopicValidationService;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.integration.AuthorizationAction;
import org.thingsboard.mqtt.broker.service.integration.IntegrationLifecycleEventPublisher;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = PublishMsgValidationServiceImpl.class)
public class PublishMsgValidationServiceImplTest {

    @MockitoBean
    DefaultTopicValidationService topicValidationService;
    @MockitoBean
    AuthorizationRuleService authorizationRuleService;
    @MockitoBean
    IntegrationLifecycleEventPublisher integrationLifecycleEventPublisher;

    @MockitoSpyBean
    PublishMsgValidationServiceImpl publishMsgValidationService;

    ClientSessionCtx ctx;

    @Before
    public void setUp() throws Exception {
        ctx = mock(ClientSessionCtx.class);
    }

    @Test
    public void givenPubMsgWithClientId_whenValidatePubMsg_thenTopicAndAuthChecked() {
        when(authorizationRuleService.isPubAuthorized(any(), any(), any())).thenReturn(true);

        PublishMsg msg = PublishMsg.builder().topicName("testTopic").build();
        publishMsgValidationService.validatePubMsg(ctx, "clientId", msg);

        verify(topicValidationService, times(1)).validateTopic(eq("testTopic"));
        verify(authorizationRuleService, times(1)).isPubAuthorized(eq("clientId"), eq("testTopic"), any());
    }

    @Test
    public void givenPubMsg_whenValidatePubMsg_thenTopicAndAuthChecked() {
        when(authorizationRuleService.isPubAuthorized(any(), any(), any())).thenReturn(true);

        PublishMsg msg = PublishMsg.builder().topicName("testTopic").build();
        publishMsgValidationService.validatePubMsg(ctx, msg);

        verify(topicValidationService, times(1)).validateTopic(eq("testTopic"));
        verify(authorizationRuleService, times(1)).isPubAuthorized(any(), eq("testTopic"), any());
    }

    @Test
    public void givenClientContextAndAllowPublishToTopic_whenValidateClientAccess_thenSuccessAndNoAuthorizationDeniedEvent() {
        when(authorizationRuleService.isPubAuthorized(any(), any(), any())).thenReturn(true);
        boolean result = publishMsgValidationService.validateClientAccess(ctx, "clientId", "topic/1");
        Assert.assertTrue(result);
        verify(integrationLifecycleEventPublisher, never()).publishAuthorizationDenied(any(), any(), any());
    }

    @Test
    public void givenClientContextAndNotAllowPublishToTopic_whenValidateClientAccess_thenFailureAndAuthorizationDeniedEventPublished() {
        when(authorizationRuleService.isPubAuthorized(any(), any(), any())).thenReturn(false);
        boolean result = publishMsgValidationService.validateClientAccess(ctx, "clientId", "topic/1");
        Assert.assertFalse(result);
        verify(integrationLifecycleEventPublisher, times(1))
                .publishAuthorizationDenied(eq(ctx), eq(AuthorizationAction.PUBLISH), eq("topic/1"));
    }

}
