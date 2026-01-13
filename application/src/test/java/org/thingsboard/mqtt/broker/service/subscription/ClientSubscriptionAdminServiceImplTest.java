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
package org.thingsboard.mqtt.broker.service.subscription;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.SubscribeCommandMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.UnsubscribeCommandMsg;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.dto.SubscriptionOptionsDto;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.subscription.ClientTopicSubscription;
import org.thingsboard.mqtt.broker.common.data.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.dao.client.application.ApplicationSharedSubscriptionService;
import org.thingsboard.mqtt.broker.dao.service.DefaultTopicValidationService;
import org.thingsboard.mqtt.broker.dto.SubscriptionInfoDto;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ClientSubscriptionAdminServiceImpl.class)
public class ClientSubscriptionAdminServiceImplTest {

    @MockBean
    ClientSessionCache clientSessionCache;
    @MockBean
    ClientSubscriptionCache clientSubscriptionCache;
    @MockBean
    ClientMqttActorManager clientMqttActorManager;
    @MockBean
    DefaultTopicValidationService topicValidationService;
    @MockBean
    ApplicationSharedSubscriptionService applicationSharedSubscriptionService;

    @SpyBean
    ClientSubscriptionAdminServiceImpl clientSubscriptionAdminService;

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testUpdateSubscriptions_ValidationMultiLvlWildcardFailed() {
        ClientSessionInfo clientSessionInfo = mock(ClientSessionInfo.class);
        when(clientSessionCache.getClientSessionInfo("clientId")).thenReturn(clientSessionInfo);
        doCallRealMethod().when(topicValidationService).validateTopicFilter(anyString());

        DataValidationException exception = assertThrows(DataValidationException.class, () ->
                clientSubscriptionAdminService.updateSubscriptions("clientId",
                        List.of(
                                new SubscriptionInfoDto("#/1", MqttQoS.AT_LEAST_ONCE, SubscriptionOptionsDto.newInstance(), null)
                        )
                ));

        assertTrue(exception.getMessage().contains("Multi-level wildcard must be the last character"));
    }

    @Test
    public void testUpdateSubscriptions_ValidationSingleLvlWildcardFailed() {
        ClientSessionInfo clientSessionInfo = mock(ClientSessionInfo.class);
        when(clientSessionCache.getClientSessionInfo("clientId")).thenReturn(clientSessionInfo);
        doCallRealMethod().when(topicValidationService).validateTopicFilter(anyString());

        DataValidationException exception = assertThrows(DataValidationException.class, () ->
                clientSubscriptionAdminService.updateSubscriptions("clientId",
                        List.of(
                                new SubscriptionInfoDto("a+/1", MqttQoS.AT_LEAST_ONCE, SubscriptionOptionsDto.newInstance(), null)
                        )
                ));

        assertTrue(exception.getMessage().contains("Single-level wildcard cannot have any character before it"));
    }

    @Test
    public void testUpdateSubscriptions_ApplicationSharedSubscriptionTopicFilterValidationFailed() {
        String topicFilter = "$share/s/test/topic";

        ClientSessionInfo clientSessionInfo = mock(ClientSessionInfo.class);
        when(clientSessionCache.getClientSessionInfo("clientId")).thenReturn(clientSessionInfo);
        when(clientSessionInfo.isPersistentAppClient()).thenReturn(true);
        when(applicationSharedSubscriptionService.findSharedSubscriptionByTopic(eq(topicFilter))).thenReturn(null);

        ThingsboardException exception = assertThrows(ThingsboardException.class, () ->
                clientSubscriptionAdminService.updateSubscriptions("clientId",
                        List.of(
                                new SubscriptionInfoDto(topicFilter, MqttQoS.AT_LEAST_ONCE, SubscriptionOptionsDto.newInstance(), null)
                        )
                ));

        assertTrue(exception.getMessage().contains("non-existent Application shared subscription"));
    }

    @Test
    public void testUpdateSubscriptions_DeviceSharedSubscriptionTopicFilterValidationOk() throws ThingsboardException {
        String topicFilter = "$share/s/test/topic";

        ClientSessionInfo clientSessionInfo = mock(ClientSessionInfo.class);
        when(clientSessionCache.getClientSessionInfo("clientId")).thenReturn(clientSessionInfo);
        when(clientSessionInfo.isPersistentAppClient()).thenReturn(false);
        when(clientSubscriptionCache.getClientSubscriptions("clientId")).thenReturn(Collections.emptySet());

        clientSubscriptionAdminService.updateSubscriptions("clientId",
                List.of(
                        new SubscriptionInfoDto(topicFilter, MqttQoS.AT_LEAST_ONCE, SubscriptionOptionsDto.newInstance(), null)
                )
        );

        verify(clientMqttActorManager).subscribe(eq("clientId"), any(SubscribeCommandMsg.class));
    }

    @Test
    public void testUpdateSubscriptions_ClientSessionNotFound() {
        when(clientSessionCache.getClientSessionInfo(anyString())).thenReturn(null);

        ThingsboardException exception = assertThrows(ThingsboardException.class, () ->
                clientSubscriptionAdminService.updateSubscriptions("clientId", Collections.emptyList()));

        assertEquals(ThingsboardErrorCode.ITEM_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    public void testUpdateSubscriptions_NoSubscriptionsToUpdate() throws ThingsboardException {
        ClientSessionInfo clientSessionInfo = mock(ClientSessionInfo.class);
        when(clientSessionCache.getClientSessionInfo("clientId")).thenReturn(clientSessionInfo);
        when(clientSubscriptionCache.getClientSubscriptions("clientId")).thenReturn(Collections.emptySet());

        clientSubscriptionAdminService.updateSubscriptions("clientId", Collections.emptyList());

        verify(clientMqttActorManager, never()).unsubscribe(anyString(), any(UnsubscribeCommandMsg.class));
        verify(clientMqttActorManager, never()).subscribe(anyString(), any(SubscribeCommandMsg.class));
    }

    @Test
    public void testUpdateSubscriptions_UnsubscribeOnly() throws ThingsboardException {
        ClientSessionInfo clientSessionInfo = mock(ClientSessionInfo.class);
        when(clientSessionCache.getClientSessionInfo("clientId")).thenReturn(clientSessionInfo);

        Set<TopicSubscription> currentSubscriptions = Set.of(new ClientTopicSubscription("unsubscribe", 1));
        when(clientSubscriptionCache.getClientSubscriptions("clientId")).thenReturn(currentSubscriptions);

        clientSubscriptionAdminService.updateSubscriptions("clientId", Collections.emptyList());

        ArgumentCaptor<UnsubscribeCommandMsg> unsubscribeCaptor = ArgumentCaptor.forClass(UnsubscribeCommandMsg.class);
        verify(clientMqttActorManager).unsubscribe(eq("clientId"), unsubscribeCaptor.capture());

        assertEquals(1, unsubscribeCaptor.getValue().getTopics().size());
        assertEquals("unsubscribe", unsubscribeCaptor.getValue().getTopics().stream().toList().get(0));
    }

    @Test
    public void testUpdateSubscriptions_SubscribeOnly() throws ThingsboardException {
        ClientSessionInfo clientSessionInfo = mock(ClientSessionInfo.class);
        when(clientSessionCache.getClientSessionInfo("clientId")).thenReturn(clientSessionInfo);

        when(clientSubscriptionCache.getClientSubscriptions("clientId")).thenReturn(Collections.emptySet());

        List<SubscriptionInfoDto> subscriptions = List.of(new SubscriptionInfoDto("subscribe", MqttQoS.AT_LEAST_ONCE, SubscriptionOptionsDto.newInstance(), null));

        clientSubscriptionAdminService.updateSubscriptions("clientId", subscriptions);

        ArgumentCaptor<SubscribeCommandMsg> subscribeCaptor = ArgumentCaptor.forClass(SubscribeCommandMsg.class);
        verify(clientMqttActorManager).subscribe(eq("clientId"), subscribeCaptor.capture());

        assertEquals(1, subscribeCaptor.getValue().getTopicSubscriptions().size());
        assertEquals("subscribe", subscribeCaptor.getValue().getTopicSubscriptions().stream().toList().get(0).getTopicFilter());
        assertEquals(1, subscribeCaptor.getValue().getTopicSubscriptions().stream().toList().get(0).getQos());
    }

    @Test
    public void testUpdateSubscriptions_SubscribeAndUnsubscribe() throws ThingsboardException {
        ClientSessionInfo clientSessionInfo = mock(ClientSessionInfo.class);
        when(clientSessionCache.getClientSessionInfo("clientId")).thenReturn(clientSessionInfo);

        Set<TopicSubscription> currentSubscriptions = Set.of(new ClientTopicSubscription("old", 0));
        when(clientSubscriptionCache.getClientSubscriptions("clientId")).thenReturn(currentSubscriptions);

        List<SubscriptionInfoDto> subscriptions = List.of(
                new SubscriptionInfoDto("new", MqttQoS.AT_LEAST_ONCE, SubscriptionOptionsDto.newInstance(), null)
        );

        clientSubscriptionAdminService.updateSubscriptions("clientId", subscriptions);

        verify(clientMqttActorManager, times(1)).unsubscribe(eq("clientId"), any(UnsubscribeCommandMsg.class));
        verify(clientMqttActorManager, times(1)).subscribe(eq("clientId"), any(SubscribeCommandMsg.class));
    }

}
