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

import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UsageStatsKVProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.ENTITY_ID_TOTAL;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.INCOMING_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.PROCESSED_BYTES;

@ExtendWith(MockitoExtension.class)
class HistoricalStatsTotalConsumerTest {

    @Mock
    private TimeseriesService timeseriesService;
    @Mock
    private HistoricalDataReportProperties historicalDataReportProperties;

    private HistoricalStatsTotalConsumer historicalStatsTotalConsumer;

    @BeforeEach
    void init() {
        var accumulator = new HistoricalStatsTotalAccumulator(timeseriesService, historicalDataReportProperties);
        historicalStatsTotalConsumer = new HistoricalStatsTotalConsumer(null, null, null,
                accumulator, timeseriesService, historicalDataReportProperties);
    }

    @Test
    void givenMsgWithUnknownRemovedKey_whenProcessSaveHistoricalStatsTotal_thenIgnoredWithoutSaveOrNpe() {
        historicalStatsTotalConsumer.processSaveHistoricalStatsTotal(buildMessage(PROCESSED_BYTES, 7));

        verify(timeseriesService, never()).save(any(), any(TsKvEntry.class));
    }

    @Test
    void givenMsgsWithKnownKey_whenProcessSaveHistoricalStatsTotal_thenEachSavesTheRunningTotal() {
        when(timeseriesService.findLatest(any(), any())).thenReturn(Futures.immediateFuture(new ArrayList<>()));
        when(timeseriesService.save(any(), any(TsKvEntry.class))).thenReturn(Futures.immediateFuture(null));

        historicalStatsTotalConsumer.processSaveHistoricalStatsTotal(buildMessage(INCOMING_MSGS, 5));
        historicalStatsTotalConsumer.processSaveHistoricalStatsTotal(buildMessage(INCOMING_MSGS, 3));

        ArgumentCaptor<TsKvEntry> saved = ArgumentCaptor.forClass(TsKvEntry.class);
        verify(timeseriesService, times(2)).save(eq(ENTITY_ID_TOTAL), saved.capture());
        assertThat(saved.getAllValues()).extracting(e -> e.getLongValue().orElseThrow()).containsExactly(5L, 8L);
    }

    private TbProtoQueueMsg<ToUsageStatsMsgProto> buildMessage(String key, int value) {
        return new TbProtoQueueMsg<>(ToUsageStatsMsgProto.newBuilder()
                .setTs(1000L)
                .setUsageStats(UsageStatsKVProto.newBuilder().setKey(key).setValue(value).build())
                .build());
    }

}
