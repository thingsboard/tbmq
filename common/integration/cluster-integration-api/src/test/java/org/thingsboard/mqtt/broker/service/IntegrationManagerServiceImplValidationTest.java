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
package org.thingsboard.mqtt.broker.service;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.data.JavaSerDesUtil;
import org.thingsboard.mqtt.broker.common.data.callback.TbCallback;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationLifecycleMsg;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.gen.integration.DownlinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationRequestProto;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationResponseProto;
import org.thingsboard.mqtt.broker.integration.api.IntegrationContext;
import org.thingsboard.mqtt.broker.integration.api.TbPlatformIntegration;
import org.thingsboard.mqtt.broker.integration.api.util.TbPlatformIntegrationUtil;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationDownlinkQueueProvider;
import org.thingsboard.mqtt.broker.service.state.ValidationTaskType;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationManagerServiceImplValidationTest {

    private TbQueueProducer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> producer;
    private IntegrationManagerServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        producer = mock(TbQueueProducer.class);
        IntegrationDownlinkQueueProvider queueProvider = mock(IntegrationDownlinkQueueProvider.class);
        when(queueProvider.getIeDownlinkProducer(any())).thenReturn(producer);
        ServiceInfoProvider serviceInfoProvider = mock(ServiceInfoProvider.class);
        when(serviceInfoProvider.getServiceId()).thenReturn("tbmq-test");
        service = new IntegrationManagerServiceImpl(null, null, serviceInfoProvider, queueProvider, null, Optional.empty());
    }

    @Test
    void anErrorReportedByTheExecutorFailsTheRequestAsABadRequest() {
        ListenableFuture<Void> future = service.checkIntegrationConnection(integration());

        service.handleValidationResponse(IntegrationValidationResponseProto.newBuilder(sentRequest())
                .setError(ByteString.copyFrom(JavaSerDesUtil.encode("Collection must not be blank"))).build(), mock(TbCallback.class));

        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(DataValidationException.class)
                .hasMessage("Collection must not be blank");
    }

    @Test
    void aResponseWithoutAnErrorCompletesTheRequest() throws Exception {
        ListenableFuture<Void> future = service.validateIntegrationConfiguration(integration());

        service.handleValidationResponse(IntegrationValidationResponseProto.newBuilder(sentRequest()).build(), mock(TbCallback.class));

        assertThat(future.get()).isNull();
    }

    @Test
    void givenValidateTask_whenValidatedLocally_thenSaveTimeChecksRunToo() throws Exception {
        IntegrationLifecycleMsg lifecycleMsg = IntegrationLifecycleMsg.builder().type(IntegrationType.KAFKA).build();
        IntegrationContext context = mock(IntegrationContext.class);
        when(context.getLifecycleMsg()).thenReturn(lifecycleMsg);
        IntegrationContextProvider contextProvider = mock(IntegrationContextProvider.class);
        when(contextProvider.buildIntegrationContext(any(Integration.class), any())).thenReturn(context);
        IntegrationManagerServiceImpl localService = new IntegrationManagerServiceImpl(null, null, null, null, contextProvider, Optional.empty());
        TbPlatformIntegration platformIntegration = mock(TbPlatformIntegration.class);
        BasicCallback callback = mock(BasicCallback.class);

        try (MockedStatic<TbPlatformIntegrationUtil> util = mockStatic(TbPlatformIntegrationUtil.class)) {
            util.when(() -> TbPlatformIntegrationUtil.createPlatformIntegration(IntegrationType.KAFKA)).thenReturn(platformIntegration);
            localService.doValidateLocally(ValidationTaskType.VALIDATE, integration(), callback);
        }

        verify(platformIntegration).validateConfiguration(eq(lifecycleMsg), anyBoolean());
        verify(platformIntegration).validateConfigurationOnSave(lifecycleMsg);
        verify(callback).onSuccess();
    }

    private IntegrationValidationResponseProto sentRequest() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> msg = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(producer).send(msg.capture(), any());
        IntegrationValidationRequestProto request = msg.getValue().getValue().getValidationRequestMsg();
        return IntegrationValidationResponseProto.newBuilder()
                .setIdMSB(request.getIdMSB())
                .setIdLSB(request.getIdLSB())
                .build();
    }

    private static Integration integration() {
        Integration integration = new Integration();
        integration.setName("test");
        integration.setType(IntegrationType.HTTP);
        integration.setConfiguration(JacksonUtil.newObjectNode());
        return integration;
    }

}
