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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.callback.TbCallback;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationEventProto;
import org.thingsboard.mqtt.broker.gen.integration.TbEventSourceProto;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationUplinkQueueProvider;
import org.thingsboard.mqtt.broker.service.IntegrationManagerService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class IntegrationUplinkConsumerTest {

    @Mock
    IntegrationUplinkQueueProvider uplinkQueueProvider;
    @Mock
    TbmqIntegrationApiService tbmqIntegrationApiService;
    @Mock
    IntegrationManagerService integrationManagerService;

    @InjectMocks
    IntegrationUplinkConsumer consumer;

    private final List<TbProtoQueueMsg<UplinkIntegrationMsgProto>> handled = new ArrayList<>();
    private final List<TbCallback> callbacks = new ArrayList<>();

    /**
     * Captures the callbacks without completing them, so the test controls when a message finishes processing.
     */
    private void captureHandling() {
        doAnswer(invocation -> {
            handled.add(invocation.getArgument(0));
            callbacks.add(invocation.getArgument(1));
            return null;
        }).when(tbmqIntegrationApiService).handle(any(), any());
    }

    @Test
    void givenTwoEventsOfSameIntegration_whenProcessPack_thenSecondIsHandledOnlyAfterFirstCompletes() {
        captureHandling();
        UUID integrationId = UUID.randomUUID();
        var first = eventMsg(integrationId);
        var second = eventMsg(integrationId);

        consumer.processPack(List.of(first, second));

        assertThat(handled).containsExactly(first);

        callbacks.get(0).onSuccess();

        assertThat(handled).containsExactly(first, second);
    }

    @Test
    void givenFailedEvent_whenProcessPack_thenNextEventOfSameIntegrationIsStillHandled() {
        captureHandling();
        UUID integrationId = UUID.randomUUID();
        var first = eventMsg(integrationId);
        var second = eventMsg(integrationId);

        consumer.processPack(List.of(first, second));
        callbacks.get(0).onFailure(new RuntimeException("boom"));

        assertThat(handled).containsExactly(first, second);
    }

    @Test
    void givenEventsOfDifferentIntegrations_whenProcessPack_thenHandledWithoutWaitingForEachOther() {
        captureHandling();
        var first = eventMsg(UUID.randomUUID());
        var second = eventMsg(UUID.randomUUID());

        consumer.processPack(List.of(first, second));

        // Neither waits for the other; the order they are dispatched in is deliberately unspecified.
        assertThat(handled).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void givenServiceInfoMsg_whenProcessPack_thenDoesNotBlockTheRestOfThePack() {
        captureHandling();
        var serviceInfo = serviceInfoMsg();
        var event = eventMsg(UUID.randomUUID());

        consumer.processPack(List.of(serviceInfo, event));

        assertThat(handled).containsExactlyInAnyOrder(serviceInfo, event);
    }

    @Test
    void givenPackOfSingleIntegration_whenAllEventsComplete_thenPackFutureCompletes() {
        captureHandling();
        UUID integrationId = UUID.randomUUID();

        var pack = consumer.processPack(List.of(eventMsg(integrationId), eventMsg(integrationId)));

        assertThat(pack).isNotDone();
        callbacks.get(0).onSuccess();
        callbacks.get(1).onSuccess();
        assertThat(pack).isDone();
    }

    @Test
    void givenPackThatNeverCompletes_whenAwaitPackProcessing_thenReturnsAfterTimeout() {
        ReflectionTestUtils.setField(consumer, "packProcessingTimeout", 50L);

        long startTime = System.currentTimeMillis();
        consumer.awaitPackProcessing(new CompletableFuture<>());

        assertThat(System.currentTimeMillis() - startTime).isGreaterThanOrEqualTo(50L);
    }

    @Test
    void givenInterruptedThread_whenAwaitPackProcessing_thenRestoresTheInterruptFlag() {
        ReflectionTestUtils.setField(consumer, "packProcessingTimeout", 50L);
        Thread.currentThread().interrupt();

        consumer.awaitPackProcessing(new CompletableFuture<>());

        // Also clears the flag, so it does not leak into the following tests.
        assertThat(Thread.interrupted()).isTrue();
    }

    private TbProtoQueueMsg<UplinkIntegrationMsgProto> eventMsg(UUID integrationId) {
        IntegrationEventProto eventProto = IntegrationEventProto.newBuilder()
                .setSource(TbEventSourceProto.INTEGRATION)
                .setEventSourceIdMSB(integrationId.getMostSignificantBits())
                .setEventSourceIdLSB(integrationId.getLeastSignificantBits())
                .build();
        return new TbProtoQueueMsg<>(integrationId,
                UplinkIntegrationMsgProto.newBuilder().setEventProto(eventProto).build());
    }

    private TbProtoQueueMsg<UplinkIntegrationMsgProto> serviceInfoMsg() {
        return new TbProtoQueueMsg<>(UUID.randomUUID(), UplinkIntegrationMsgProto.newBuilder()
                .setServiceInfoProto(ServiceInfo.getDefaultInstance())
                .build());
    }

}
