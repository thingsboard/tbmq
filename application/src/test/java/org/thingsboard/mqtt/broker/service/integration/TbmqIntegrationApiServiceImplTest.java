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
package org.thingsboard.mqtt.broker.service.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.callback.TbCallback;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationEventProto;
import org.thingsboard.mqtt.broker.gen.integration.TbEventSourceProto;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TbmqIntegrationApiServiceImplTest {

    @Mock
    PlatformIntegrationService platformIntegrationService;

    @InjectMocks
    TbmqIntegrationApiServiceImpl service;

    @Test
    void givenServiceInfoMsg_whenHandle_thenCompletesCallback() {
        ServiceInfo serviceInfo = ServiceInfo.getDefaultInstance();
        TbCallback callback = mock(TbCallback.class);

        service.handle(serviceInfoEnvelope(serviceInfo), callback);

        verify(platformIntegrationService).processServiceInfo(serviceInfo);
        verify(callback).onSuccess();
    }

    /**
     * Every branch has to terminate the callback, since IntegrationUplinkConsumer waits on it before dispatching the
     * next event of the same integration - a branch that leaves it pending stalls the whole pack until the timeout.
     */
    @Test
    void givenEventMsg_whenHandle_thenHandsTheCallbackDownToProcessUplinkData() {
        IntegrationEventProto eventProto = IntegrationEventProto.newBuilder()
                .setSource(TbEventSourceProto.INTEGRATION)
                .build();
        TbCallback callback = mock(TbCallback.class);

        service.handle(eventEnvelope(eventProto), callback);

        ArgumentCaptor<IntegrationApiCallback> captor = ArgumentCaptor.forClass(IntegrationApiCallback.class);
        verify(platformIntegrationService).processUplinkData(eq(eventProto), captor.capture());
        // the callback handed down is the one that terminates the consumer's wait
        captor.getValue().onSuccess(null);
        verify(callback).onSuccess();
    }

    @Test
    void givenUnsupportedMsg_whenHandle_thenFailsCallback() {
        TbCallback callback = mock(TbCallback.class);

        service.handle(new TbProtoQueueMsg<>(UUID.randomUUID(), UplinkIntegrationMsgProto.getDefaultInstance()), callback);

        verifyNoInteractions(platformIntegrationService);
        verify(callback).onFailure(any(IllegalArgumentException.class));
    }

    private TbProtoQueueMsg<UplinkIntegrationMsgProto> serviceInfoEnvelope(ServiceInfo serviceInfo) {
        return new TbProtoQueueMsg<>(UUID.randomUUID(),
                UplinkIntegrationMsgProto.newBuilder().setServiceInfoProto(serviceInfo).build());
    }

    private TbProtoQueueMsg<UplinkIntegrationMsgProto> eventEnvelope(IntegrationEventProto eventProto) {
        return new TbProtoQueueMsg<>(UUID.randomUUID(),
                UplinkIntegrationMsgProto.newBuilder().setEventProto(eventProto).build());
    }

}
