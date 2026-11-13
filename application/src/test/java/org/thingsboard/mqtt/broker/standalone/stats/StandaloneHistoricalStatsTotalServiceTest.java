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
package org.thingsboard.mqtt.broker.standalone.stats;

import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UsageStatsKVProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.historical.stats.HistoricalStatsTotalAccumulator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandaloneHistoricalStatsTotalServiceTest {

    @Mock
    private TimeseriesService timeseriesService;
    @Mock
    private HistoricalDataReportProperties historicalDataReportProperties;
    @Mock
    private TbQueueCallback callback;

    private StandaloneHistoricalStatsTotalService service;

    @BeforeEach
    void setUp() {
        service = new StandaloneHistoricalStatsTotalService(
                new HistoricalStatsTotalAccumulator(timeseriesService, historicalDataReportProperties),
                timeseriesService, historicalDataReportProperties);
    }

    @Test
    void givenEnabled_whenPublish_thenOneTotalEntryIsSavedWithTheProtoTsKeyAndValueThenOnSuccess() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        when(timeseriesService.save(eq(BrokerConstants.ENTITY_ID_TOTAL), any(TsKvEntry.class)))
                .thenReturn(Futures.immediateFuture(null));
        nothingStored();

        service.publish(usageMsg("incomingMsgs", 7L, 1000L), callback);

        ArgumentCaptor<TsKvEntry> captor = ArgumentCaptor.forClass(TsKvEntry.class);
        verify(timeseriesService).save(eq(BrokerConstants.ENTITY_ID_TOTAL), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(BasicTsKvEntry.class);
        assertThat(captor.getValue().getTs()).isEqualTo(1000L);
        assertThat(captor.getValue().getKey()).isEqualTo("incomingMsgs");
        assertThat(captor.getValue().getLongValue()).contains(7L);
        verify(callback).onSuccess(isNull());
        verify(callback, never()).onFailure(any());
    }

    @Test
    void givenSaveFails_whenPublish_thenOnFailureIsCalledWithTheCause() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        RuntimeException cause = new RuntimeException("store down");
        when(timeseriesService.save(eq(BrokerConstants.ENTITY_ID_TOTAL), any(TsKvEntry.class)))
                .thenReturn(Futures.immediateFailedFuture(cause));
        nothingStored();

        service.publish(usageMsg("incomingMsgs", 7L, 1000L), callback);

        verify(callback).onFailure(cause);
        verify(callback, never()).onSuccess(any());
    }

    @Test
    void givenDisabled_whenPublish_thenNothingIsSaved() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(true);

        service.publish(usageMsg("incomingMsgs", 7L, 1000L), callback);

        verifyNoInteractions(timeseriesService, callback);
    }

    @Test
    void givenAKeyThatIsNotATotalKey_whenPublish_thenNothingIsSavedThenOnSuccess() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);

        service.publish(usageMsg(BrokerConstants.PROCESSED_BYTES, 7L, 1000L), callback);

        verifyNoInteractions(timeseriesService);
        verify(callback).onSuccess(isNull());
    }

    @Test
    void givenStoredLatestTotalAtTheSameTs_whenFirstPublish_thenTheSavedValueIsTheSum() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        when(historicalDataReportProperties.getInterval()).thenReturn(1);
        stored("incomingMsgs", 60_000L, 10L);
        savesSucceed();

        service.publish(usageMsg("incomingMsgs", 5L, 60_000L), callback);

        assertThat(savedValues()).containsExactly(15L);
        verify(callback).onSuccess(isNull());
    }

    @Test
    void givenStoredLatestTotalAtAnOlderIntervalStart_whenFirstPublish_thenTheSavedValueIsTheMsgValue() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        when(historicalDataReportProperties.getInterval()).thenReturn(1);
        stored("incomingMsgs", 60_000L, 10L);
        savesSucceed();

        service.publish(usageMsg("incomingMsgs", 5L, 120_000L), callback);

        assertThat(savedValues()).containsExactly(5L);
    }

    @Test
    void givenStoredLatestTotalOlderThanOneInterval_whenFirstPublish_thenTheStoredValueIsIgnored() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        when(historicalDataReportProperties.getInterval()).thenReturn(1);
        stored("incomingMsgs", 60_000L, 10L);
        savesSucceed();

        service.publish(usageMsg("incomingMsgs", 5L, 600_000L), callback);

        assertThat(savedValues()).containsExactly(5L);
    }

    @Test
    void givenTwoPublishesWithTheSameTsAndKey_whenPublish_thenTheValuesAreAccumulated() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        nothingStored();
        savesSucceed();

        service.publish(usageMsg("incomingMsgs", 4L, 60_000L), callback);
        service.publish(usageMsg("incomingMsgs", 6L, 60_000L), callback);
        service.publish(usageMsg("outgoingMsgs", 1L, 60_000L), callback);
        service.publish(usageMsg("incomingMsgs", 2L, 120_000L), callback);

        assertThat(savedValues()).containsExactly(4L, 10L, 1L, 2L);
    }

    @Test
    void givenAnOlderTsThanTheHeldOne_whenPublish_thenItIsSavedAsItIsAndTheHeldTotalIsKept() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        nothingStored();
        savesSucceed();

        service.publish(usageMsg("incomingMsgs", 4L, 120_000L), callback);
        service.publish(usageMsg("incomingMsgs", 9L, 60_000L), callback);
        service.publish(usageMsg("incomingMsgs", 1L, 120_000L), callback);

        assertThat(savedValues()).containsExactly(4L, 9L, 5L);
    }

    @Test
    void givenStoredTotalLookupFails_whenPublish_thenOnFailureIsCalledAndNothingIsSaved() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        RuntimeException cause = new RuntimeException("store down");
        when(timeseriesService.findLatest(eq(BrokerConstants.ENTITY_ID_TOTAL), anyList()))
                .thenReturn(Futures.immediateFailedFuture(cause));

        service.publish(usageMsg("incomingMsgs", 4L, 60_000L), callback);

        verify(callback).onFailure(any());
        verify(timeseriesService, never()).save(anyString(), any(TsKvEntry.class));
    }

    private void nothingStored() {
        when(timeseriesService.findLatest(eq(BrokerConstants.ENTITY_ID_TOTAL), anyList()))
                .thenReturn(Futures.immediateFuture(List.of()));
    }

    private void stored(String key, long ts, long value) {
        when(timeseriesService.findLatest(eq(BrokerConstants.ENTITY_ID_TOTAL), anyList()))
                .thenReturn(Futures.immediateFuture(List.of(new BasicTsKvEntry(ts, new LongDataEntry(key, value)))));
    }

    private void savesSucceed() {
        when(timeseriesService.save(eq(BrokerConstants.ENTITY_ID_TOTAL), any(TsKvEntry.class)))
                .thenReturn(Futures.immediateFuture(null));
    }

    private List<Long> savedValues() {
        ArgumentCaptor<TsKvEntry> captor = ArgumentCaptor.forClass(TsKvEntry.class);
        verify(timeseriesService, atLeastOnce()).save(eq(BrokerConstants.ENTITY_ID_TOTAL), captor.capture());
        return captor.getAllValues().stream().map(e -> e.getLongValue().orElseThrow()).toList();
    }

    private static ToUsageStatsMsgProto usageMsg(String key, long value, long ts) {
        return ToUsageStatsMsgProto.newBuilder()
                .setServiceId("service-1")
                .setTs(ts)
                .setUsageStats(UsageStatsKVProto.newBuilder().setKey(key).setValue(value).build())
                .build();
    }

}
