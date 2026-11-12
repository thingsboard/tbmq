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
package org.thingsboard.mqtt.broker.service.historical.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UsageStatsKVProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.HistoricalDataQueueFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaHistoricalStatsTotalPublisherTest {

    @Mock
    private HistoricalDataQueueFactory historicalDataQueueFactory;
    @Mock
    private ServiceInfoProvider serviceInfoProvider;
    @Mock
    private HistoricalStatsTotalHelper helper;
    @Mock
    private HistoricalDataReportProperties historicalDataReportProperties;
    @Mock
    private TbQueueProducer<TbProtoQueueMsg<ToUsageStatsMsgProto>> producer;
    @Mock
    private TbQueueCallback callback;

    private KafkaHistoricalStatsTotalPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaHistoricalStatsTotalPublisher(
                historicalDataQueueFactory, serviceInfoProvider, helper, historicalDataReportProperties);
    }

    @Test
    void givenReportingEnabled_whenInit_thenProducerIsCreatedForTheServiceId() {
        when(historicalDataReportProperties.isEnabled()).thenReturn(true);
        when(serviceInfoProvider.getServiceId()).thenReturn("service-1");
        when(historicalDataQueueFactory.createProducer("service-1")).thenReturn(producer);

        publisher.init();

        verify(historicalDataQueueFactory).createProducer("service-1");
    }

    @Test
    void givenReportingDisabled_whenInitAndDestroy_thenNoProducerIsCreated() {
        when(historicalDataReportProperties.isEnabled()).thenReturn(false);

        publisher.init();
        publisher.destroy();

        verifyNoInteractions(historicalDataQueueFactory);
    }

    @Test
    void givenInitialisedPublisher_whenPublish_thenProtoIsSentToTheHelperTopicWithNullKeyAndTheCallback() {
        initEnabled();
        when(helper.getTopic()).thenReturn("total-topic");
        ToUsageStatsMsgProto msg = usageMsg("incomingMsgs", 7L, 1000L);

        publisher.publish(msg, callback);

        ArgumentCaptor<TbProtoQueueMsg<ToUsageStatsMsgProto>> captor = ArgumentCaptor.forClass(TbProtoQueueMsg.class);
        verify(producer).send(eq("total-topic"), isNull(), captor.capture(), same(callback));
        assertThat(captor.getValue().getValue()).isEqualTo(msg);
    }

    @Test
    void givenInitialisedPublisher_whenDestroy_thenProducerIsStopped() {
        initEnabled();

        publisher.destroy();

        verify(producer).stop();
    }

    @Test
    void givenInitialisedPublisher_whenPublishThenDestroy_thenProducerStopsAfterTheLastSend() {
        initEnabled();
        when(helper.getTopic()).thenReturn("total-topic");

        publisher.publish(usageMsg("incomingMsgs", 1L, 1L), callback);
        publisher.destroy();

        var order = inOrder(producer);
        order.verify(producer).send(any(), any(), any(), any());
        order.verify(producer).stop();
    }

    private void initEnabled() {
        when(historicalDataReportProperties.isEnabled()).thenReturn(true);
        when(serviceInfoProvider.getServiceId()).thenReturn("service-1");
        when(historicalDataQueueFactory.createProducer("service-1")).thenReturn(producer);
        publisher.init();
    }

    static ToUsageStatsMsgProto usageMsg(String key, long value, long ts) {
        return ToUsageStatsMsgProto.newBuilder()
                .setServiceId("service-1")
                .setTs(ts)
                .setUsageStats(UsageStatsKVProto.newBuilder().setKey(key).setValue(value).build())
                .build();
    }

}
