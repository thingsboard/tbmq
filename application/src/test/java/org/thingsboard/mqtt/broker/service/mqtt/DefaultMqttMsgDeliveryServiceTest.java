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
package org.thingsboard.mqtt.broker.service.mqtt;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.historical.stats.TbMessageStatsReportClient;
import org.thingsboard.mqtt.broker.service.mqtt.delivery.BufferedMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.delivery.MqttPublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultMqttMsgDeliveryServiceTest {

    @Mock
    MqttMessageGenerator mqttMessageGenerator;
    @Mock
    TbMessageStatsReportClient tbMessageStatsReportClient;
    @Mock
    BufferedMsgDeliveryService bufferedMsgDeliveryService;
    @Mock
    MqttPublishMsgDeliveryService mqttPublishMsgDeliveryService;
    @Mock
    ClientSessionCtx sessionCtx;

    @InjectMocks
    DefaultMqttMsgDeliveryService deliveryService;

    @Before
    public void setUp() {
        when(sessionCtx.isWritable()).thenReturn(false);
    }

    @Test
    public void givenChannelNotWritable_whenSendPublishMsgProto_thenReportsDroppedMsg() {
        PublishMsgProto msg = publishMsgProto(false);

        deliveryService.sendPublishMsgProtoToClient(sessionCtx, msg);

        verify(tbMessageStatsReportClient, times(1)).reportDroppedMsgs();
        verify(bufferedMsgDeliveryService, never()).sendPublishMsgToRegularClient(any(), any());
    }

    @Test
    public void givenChannelNotWritable_whenSendRetainedPublishMsgProto_thenDoesNotReportDroppedMsg() {
        PublishMsgProto msg = publishMsgProto(true);

        deliveryService.sendPublishMsgProtoToClient(sessionCtx, msg);

        verify(tbMessageStatsReportClient, never()).reportDroppedMsgs();
        verify(bufferedMsgDeliveryService, never()).sendPublishMsgToRegularClient(any(), any());
    }

    private PublishMsgProto publishMsgProto(boolean retain) {
        return PublishMsgProto.newBuilder()
                .setTopicName("test/topic")
                .setQos(1)
                .setRetain(retain)
                .build();
    }
}
