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
package org.thingsboard.mqtt.broker.service.mqtt.sparkplug;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgProcessor;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class SparkplugCertificateRepublisherImplTest {

    private MsgDispatcherService msgDispatcherService;
    private RetainedMsgProcessor retainedMsgProcessor;
    private SparkplugCertificateRepublisherImpl republisher;
    private SessionInfo sessionInfo;
    private final String clientCertCn = "cn-edge";

    @Before
    public void setUp() {
        msgDispatcherService = mock(MsgDispatcherService.class);
        retainedMsgProcessor = mock(RetainedMsgProcessor.class);
        when(retainedMsgProcessor.process(any())).thenAnswer(inv -> inv.getArgument(0));
        sessionInfo = mock(SessionInfo.class);
        republisher = new SparkplugCertificateRepublisherImpl(msgDispatcherService, retainedMsgProcessor);
    }

    @Test
    public void givenNbirthPublish_whenMaybeRepublish_thenDispatchesCertificateTopicWithRetainTrueAndSamePayload() {
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        PublishMsg original = PublishMsg.builder()
                .packetId(42)
                .topicName("spBv1.0/G1/NBIRTH/E1")
                .payload(payload)
                .qos(0)
                .isRetained(false)
                .isDup(false)
                .build();

        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        ArgumentCaptor<PublishMsg> captor = ArgumentCaptor.forClass(PublishMsg.class);
        verify(msgDispatcherService).persistPublishMsg(eq(sessionInfo), captor.capture(), eq(clientCertCn), any());
        PublishMsg sent = captor.getValue();
        assertThat(sent.getTopicName()).isEqualTo("$sparkplug/certificates/spBv1.0/G1/NBIRTH/E1");
        assertThat(sent.isRetained()).isTrue();
        assertThat(sent.getPayload()).isSameAs(payload);
        assertThat(sent.getQos()).isEqualTo(0);
    }

    @Test
    public void givenDbirthPublish_whenMaybeRepublish_thenDispatchesCertificateTopicWithRetainTrueAndSamePayload() {
        byte[] payload = new byte[]{9, 8, 7};
        PublishMsg original = PublishMsg.builder()
                .topicName("spBv1.0/G1/DBIRTH/E1/D1")
                .payload(payload)
                .qos(1)
                .isRetained(false)
                .isDup(false)
                .build();

        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        ArgumentCaptor<PublishMsg> captor = ArgumentCaptor.forClass(PublishMsg.class);
        verify(msgDispatcherService).persistPublishMsg(eq(sessionInfo), captor.capture(), eq(clientCertCn), any());
        PublishMsg sent = captor.getValue();
        assertThat(sent.getTopicName()).isEqualTo("$sparkplug/certificates/spBv1.0/G1/DBIRTH/E1/D1");
        assertThat(sent.isRetained()).isTrue();
        assertThat(sent.getPayload()).isSameAs(payload);
        assertThat(sent.getQos()).isEqualTo(1);
    }

    @Test
    public void givenNdataPublish_whenMaybeRepublish_thenDoesNotDispatch() {
        PublishMsg original = PublishMsg.builder()
                .topicName("spBv1.0/G1/NDATA/E1")
                .payload(new byte[]{1})
                .qos(0)
                .isRetained(false)
                .isDup(false)
                .build();

        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        verify(msgDispatcherService, never()).persistPublishMsg(any(), any(), any(), any());
    }

    @Test
    public void givenNonSparkplugPublish_whenMaybeRepublish_thenDoesNotDispatch() {
        PublishMsg original = PublishMsg.builder()
                .topicName("sensors/temperature")
                .payload(new byte[]{1})
                .qos(0)
                .isRetained(false)
                .isDup(false)
                .build();

        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        verify(msgDispatcherService, never()).persistPublishMsg(any(), any(), any(), any());
    }

    @Test
    public void givenAlreadyCertificatePublish_whenMaybeRepublish_thenDoesNotDispatchToPreventLoop() {
        PublishMsg original = PublishMsg.builder()
                .topicName("$sparkplug/certificates/spBv1.0/G1/NBIRTH/E1")
                .payload(new byte[]{1})
                .qos(0)
                .isRetained(true)
                .isDup(false)
                .build();

        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        verify(msgDispatcherService, never()).persistPublishMsg(any(), any(), any(), any());
    }

    @Test
    public void givenNbirthPublishWithQos2_whenMaybeRepublish_thenPreservesQosOnCertificate() {
        PublishMsg original = PublishMsg.builder()
                .topicName("spBv1.0/G1/NBIRTH/E1")
                .payload(new byte[]{1})
                .qos(2)
                .isRetained(false)
                .isDup(false)
                .build();

        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        ArgumentCaptor<PublishMsg> captor = ArgumentCaptor.forClass(PublishMsg.class);
        verify(msgDispatcherService).persistPublishMsg(eq(sessionInfo), captor.capture(), eq(clientCertCn), any());
        assertThat(captor.getValue().getQos()).isEqualTo(2);
    }

    @Test
    public void givenNbirthPublish_whenMaybeRepublish_thenStoresRetainedBeforeDispatch() {
        byte[] payload = new byte[]{42};
        PublishMsg original = PublishMsg.builder()
                .topicName("spBv1.0/G1/NBIRTH/E1")
                .payload(payload)
                .qos(1)
                .isRetained(false)
                .isDup(false)
                .build();

        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        InOrder order = inOrder(retainedMsgProcessor, msgDispatcherService);
        ArgumentCaptor<PublishMsg> retainedCaptor = ArgumentCaptor.forClass(PublishMsg.class);
        order.verify(retainedMsgProcessor).process(retainedCaptor.capture());
        order.verify(msgDispatcherService).persistPublishMsg(eq(sessionInfo), any(), eq(clientCertCn), any());

        PublishMsg storedAsRetained = retainedCaptor.getValue();
        assertThat(storedAsRetained.getTopicName()).isEqualTo("$sparkplug/certificates/spBv1.0/G1/NBIRTH/E1");
        assertThat(storedAsRetained.isRetained()).isTrue();
        assertThat(storedAsRetained.getPayload()).isSameAs(payload);
    }

    @Test
    public void givenDbirthPublish_whenMaybeRepublish_thenStoresRetainedBeforeDispatch() {
        byte[] payload = new byte[]{7};
        PublishMsg original = PublishMsg.builder()
                .topicName("spBv1.0/G1/DBIRTH/E1/D1")
                .payload(payload)
                .qos(0)
                .isRetained(false)
                .isDup(false)
                .build();

        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        InOrder order = inOrder(retainedMsgProcessor, msgDispatcherService);
        ArgumentCaptor<PublishMsg> retainedCaptor = ArgumentCaptor.forClass(PublishMsg.class);
        order.verify(retainedMsgProcessor).process(retainedCaptor.capture());
        order.verify(msgDispatcherService).persistPublishMsg(eq(sessionInfo), any(), eq(clientCertCn), any());

        PublishMsg storedAsRetained = retainedCaptor.getValue();
        assertThat(storedAsRetained.getTopicName()).isEqualTo("$sparkplug/certificates/spBv1.0/G1/DBIRTH/E1/D1");
        assertThat(storedAsRetained.isRetained()).isTrue();
        assertThat(storedAsRetained.getPayload()).isSameAs(payload);
    }

    @Test
    public void givenNonSparkplugPublish_whenMaybeRepublish_thenDoesNotStoreRetained() {
        PublishMsg original = PublishMsg.builder()
                .topicName("sensors/temperature")
                .payload(new byte[]{1})
                .qos(0)
                .isRetained(false)
                .isDup(false)
                .build();

        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        verify(retainedMsgProcessor, never()).process(any());
    }

    @Test
    public void givenRetainedMsgProcessorThrows_whenMaybeRepublish_thenSwallowsExceptionAndDoesNotDispatch() {
        PublishMsg original = PublishMsg.builder()
                .topicName("spBv1.0/G1/NBIRTH/E1")
                .payload(new byte[]{1})
                .qos(1)
                .isRetained(false)
                .isDup(false)
                .build();
        when(retainedMsgProcessor.process(any())).thenThrow(new RuntimeException("retain store down"));

        // Must not propagate — the §10.1.4 hook must never break the primary publish path.
        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        verifyNoInteractions(msgDispatcherService);
    }

    @Test
    public void givenMsgDispatcherThrows_whenMaybeRepublish_thenSwallowsException() {
        PublishMsg original = PublishMsg.builder()
                .topicName("spBv1.0/G1/DBIRTH/E1/D1")
                .payload(new byte[]{1})
                .qos(1)
                .isRetained(false)
                .isDup(false)
                .build();
        doThrow(new RuntimeException("kafka down"))
                .when(msgDispatcherService).persistPublishMsg(any(), any(), any(), any());

        // Must not propagate — the §10.1.4 hook must never break the primary publish path.
        republisher.maybeRepublish(sessionInfo, original, clientCertCn);

        // retain-store was still attempted before the dispatch failure
        verify(retainedMsgProcessor).process(any());
    }
}
