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
package org.thingsboard.mqtt.broker.service.processing.downlink.basic;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = BasicDownLinkProcessorImpl.class)
public class BasicDownLinkProcessorImplTest {

    @MockitoBean
    ClientSessionCtxService clientSessionCtxService;
    @MockitoBean
    MqttMsgDeliveryService mqttMsgDeliveryService;
    @MockitoBean
    ClientLogger clientLogger;
    @MockitoBean
    RateLimitService rateLimitService;
    @MockitoBean
    TbMessageStatsReportClient tbMessageStatsReportClient;

    @MockitoSpyBean
    BasicDownLinkProcessorImpl basicDownLinkProcessor;

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void givenPublishMsg_whenProcessAndNoSession_thenDoNothing() {
        String clientId = "clientId";
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(clientSessionCtxService.getClientSessionCtx(clientId)).thenReturn(null);

        basicDownLinkProcessor.process(clientId, publishMsgProto);

        verify(mqttMsgDeliveryService, never()).sendPublishMsgProtoToClient(any(), any());
        verify(tbMessageStatsReportClient, never()).reportStats(eq(DROPPED_MSGS));
    }

    @Test
    public void givenPublishMsg_whenProcessAndRateLimitsNotReached_thenPublishMsg() {
        String clientId = "clientId";
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(clientSessionCtxService.getClientSessionCtx(clientId)).thenReturn(new ClientSessionCtx());
        when(rateLimitService.checkOutgoingLimits(clientId, publishMsgProto)).thenReturn(true);

        basicDownLinkProcessor.process(clientId, publishMsgProto);

        verify(mqttMsgDeliveryService, times(1)).sendPublishMsgProtoToClient(any(), any());
        verify(tbMessageStatsReportClient, never()).reportStats(eq(DROPPED_MSGS));
    }

    @Test
    public void givenPublishMsg_whenProcessAndRateLimitsReached_thenDisconnectClient() {
        String clientId = "clientId";
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(clientSessionCtxService.getClientSessionCtx(clientId)).thenReturn(new ClientSessionCtx());
        when(rateLimitService.checkOutgoingLimits(clientId, publishMsgProto)).thenReturn(false);

        basicDownLinkProcessor.process(clientId, publishMsgProto);

        verify(mqttMsgDeliveryService, never()).sendPublishMsgProtoToClient(any(), any());
        verify(tbMessageStatsReportClient).reportStats(eq(DROPPED_MSGS));
    }

    @Test
    public void givenSubscriptionAndPublishMsg_whenProcessAndNoSession_thenDoNothing() {
        String clientId = "clientId";
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(clientSessionCtxService.getClientSessionCtx(clientId)).thenReturn(null);

        basicDownLinkProcessor.process(getSubscription(clientId), publishMsgProto);

        verify(mqttMsgDeliveryService, never()).sendPublishMsgProtoToClient(any(), any(), any());
        verify(tbMessageStatsReportClient, never()).reportStats(eq(DROPPED_MSGS));
    }

    @Test
    public void givenSubscriptionAndPublishMsg_whenProcessAndRateLimitsNotReached_thenPublishMsg() {
        String clientId = "clientId";
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(clientSessionCtxService.getClientSessionCtx(clientId)).thenReturn(new ClientSessionCtx());
        when(rateLimitService.checkOutgoingLimits(clientId, publishMsgProto)).thenReturn(true);

        basicDownLinkProcessor.process(getSubscription(clientId), publishMsgProto);

        verify(mqttMsgDeliveryService, times(1)).sendPublishMsgProtoToClient(any(), any(), any());
        verify(tbMessageStatsReportClient, never()).reportStats(eq(DROPPED_MSGS));
    }

    @Test
    public void givenSubscriptionAndPublishMsg_whenProcessAndRateLimitsReached_thenDisconnectClient() {
        String clientId = "clientId";
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(clientSessionCtxService.getClientSessionCtx(clientId)).thenReturn(new ClientSessionCtx());
        when(rateLimitService.checkOutgoingLimits(clientId, publishMsgProto)).thenReturn(false);

        basicDownLinkProcessor.process(getSubscription(clientId), publishMsgProto);

        verify(mqttMsgDeliveryService, never()).sendPublishMsgProtoToClient(any(), any(), any());
        verify(tbMessageStatsReportClient).reportStats(eq(DROPPED_MSGS));
    }

    private Subscription getSubscription(String clientId) {
        return new Subscription("topic", 1, ClientSessionInfo.builder().clientId(clientId).build());
    }
}
