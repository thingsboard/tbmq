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
package org.thingsboard.mqtt.broker.service.processing.downlink;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.processing.downlink.basic.BasicDownLinkProcessor;
import org.thingsboard.mqtt.broker.service.processing.downlink.persistent.PersistentDownLinkProcessor;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DownLinkProxyImplTest {

    ServiceInfoProvider serviceInfoProvider;
    DownLinkQueuePublisher queuePublisher;
    BasicDownLinkProcessor basicDownLinkProcessor;
    PersistentDownLinkProcessor persistentDownLinkProcessor;

    DownLinkProxyImpl downLinkProxy;

    @Before
    public void setUp() throws Exception {
        serviceInfoProvider = mock(ServiceInfoProvider.class);
        queuePublisher = mock(DownLinkQueuePublisher.class);
        basicDownLinkProcessor = mock(BasicDownLinkProcessor.class);
        persistentDownLinkProcessor = mock(PersistentDownLinkProcessor.class);

        downLinkProxy = spy(new DownLinkProxyImpl(serviceInfoProvider, queuePublisher, basicDownLinkProcessor, persistentDownLinkProcessor));
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void givenPubMsgForSubscriberOnSameBroker_whenSendBasicMsg_thenPublishMsgToSubscriber() {
        String serviceId = "broker-0";
        String clientId = "clientId";
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(serviceInfoProvider.getServiceId()).thenReturn(serviceId);
        downLinkProxy.sendBasicMsg(serviceId, clientId, publishMsgProto);

        verify(basicDownLinkProcessor, times(1)).process(eq(clientId), eq(publishMsgProto));
    }

    @Test
    public void givenPubMsgForSubscriberOnDifferentBroker_whenSendBasicMsg_thenPublishMsgToAnotherBroker() {
        String serviceId = "broker-0";
        String clientId = "clientId";
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(serviceInfoProvider.getServiceId()).thenReturn("broker-1");
        downLinkProxy.sendBasicMsg(serviceId, clientId, publishMsgProto);

        verify(queuePublisher, times(1)).publishBasicMsg(eq(serviceId), eq(clientId), eq(publishMsgProto));
    }

    @Test
    public void givenPubMsgWithSubscriptionForSubscriberOnSameBroker_whenSendBasicMsg_thenPublishMsgToSubscriber() {
        String serviceId = "broker-0";
        String clientId = "clientId";

        ClientSessionInfo sessionInfo = ClientSessionInfo.builder().serviceId(serviceId).clientId(clientId).build();
        Subscription subscription = new Subscription("#", 1, sessionInfo);
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(serviceInfoProvider.getServiceId()).thenReturn(serviceId);
        downLinkProxy.sendBasicMsg(subscription, publishMsgProto);

        verify(basicDownLinkProcessor, times(1)).process(eq(subscription), eq(publishMsgProto));
    }

    @Test
    public void givenPubMsgWithSubscriptionForSubscriberOnDifferentBroker_whenSendBasicMsg_thenPublishMsgToAnotherBroker() {
        String serviceId = "broker-0";
        String clientId = "clientId";

        ClientSessionInfo sessionInfo = ClientSessionInfo.builder().serviceId(serviceId).clientId(clientId).build();
        Subscription subscription = new Subscription("#", 1, sessionInfo);
        PublishMsgProto publishMsgProto = PublishMsgProto.newBuilder().build();

        when(serviceInfoProvider.getServiceId()).thenReturn("broker-1");
        downLinkProxy.sendBasicMsg(subscription, publishMsgProto);

        verify(queuePublisher, times(1)).publishBasicMsg(eq(serviceId), eq(clientId), eq(publishMsgProto));
    }

    @Test
    public void givenPubMsgForSubscriberOnSameBroker_whenSendPersistentMsg_thenPublishMsgToSubscriber() {
        String serviceId = "broker-0";
        String clientId = "clientId";
        DevicePublishMsg devicePublishMsg = DevicePublishMsg.builder().build();

        when(serviceInfoProvider.getServiceId()).thenReturn(serviceId);
        downLinkProxy.sendPersistentMsg(serviceId, clientId, devicePublishMsg);

        verify(persistentDownLinkProcessor, times(1)).process(eq(clientId), eq(devicePublishMsg));
    }

    @Test
    public void givenPubMsgForSubscriberOnDifferentBroker_whenSendPersistentMsg_thenPublishMsgToAnotherBroker() {
        String serviceId = "broker-0";
        String clientId = "clientId";
        DevicePublishMsg devicePublishMsg = DevicePublishMsg.builder().build();

        when(serviceInfoProvider.getServiceId()).thenReturn("broker-1");
        downLinkProxy.sendPersistentMsg(serviceId, clientId, devicePublishMsg);

        verify(queuePublisher, times(1)).publishPersistentMsg(eq(serviceId), eq(clientId), eq(devicePublishMsg));
    }

}
