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
package org.thingsboard.mqtt.broker.actors.client.service.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventResponseProto;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClientSessionEventQueueFactory;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueClientSessionEventResponseSenderTest {

    @Mock
    ClientSessionEventQueueFactory factory;
    @Mock
    ServiceInfoProvider serviceInfoProvider;
    @Mock
    TbQueueProducer<TbProtoQueueMsg<ClientSessionEventResponseProto>> producer;

    @Test
    void givenConnectResponse_whenSend_thenProducerReceivesResponseWithRequestIdHeaderAndProducerStoppedOnDestroy() {
        when(serviceInfoProvider.getServiceId()).thenReturn("node-1");
        when(factory.createEventResponseProducer("node-1")).thenReturn(producer);
        QueueClientSessionEventResponseSender sender = new QueueClientSessionEventResponseSender(factory, serviceInfoProvider);
        ReflectionTestUtils.setField(sender, "responseSenderThreads", 1);
        sender.init();

        ClientSessionEventResponseProto response = ClientSessionEventResponseProto.newBuilder().setSuccess(true).build();
        UUID requestId = UUID.randomUUID();
        sender.send("client-1", new ConnectionRequestInfo(requestId, 0L, "tbmq.client.session.event.response.node-2"), response);

        verify(producer, timeout(2000)).send(eq("tbmq.client.session.event.response.node-2"), isNull(),
                argThat(m -> m.getKey().equals("client-1") && m.getValue() == response
                        && Arrays.equals(m.getHeaders().get(BrokerConstants.REQUEST_ID_HEADER), BytesUtil.uuidToBytes(requestId))), any());

        sender.destroy();
        verify(producer).stop();
    }
}
