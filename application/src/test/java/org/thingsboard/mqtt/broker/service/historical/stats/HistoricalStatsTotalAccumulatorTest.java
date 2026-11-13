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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UsageStatsKVProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DROPPED_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.INCOMING_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.OUTGOING_MSGS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.PROCESSED_BYTES;


@Slf4j
@ExtendWith(MockitoExtension.class)
class HistoricalStatsTotalAccumulatorTest {

    private TimeseriesService timeseriesService;
    private HistoricalDataReportProperties historicalDataReportProperties;

    private HistoricalStatsTotalAccumulator accumulator;

    private long ts;

    @BeforeEach
    void init() {
        ts = System.currentTimeMillis();
        timeseriesService = mock(TimeseriesService.class);
        historicalDataReportProperties = mock(HistoricalDataReportProperties.class);
        accumulator = new HistoricalStatsTotalAccumulator(timeseriesService, historicalDataReportProperties);
    }

    @Test
    void givenStatsMsg_whenReceiveIncomingMessage_thenCalculateTsTotalStatsForIncomingMsgs() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        var msg1 = buildMessage(INCOMING_MSGS, 5);
        var msg2 = buildMessage(INCOMING_MSGS, 10);
        var msg3 = buildMessage(INCOMING_MSGS, 25);
        var msg4 = buildMessage(INCOMING_MSGS, 3);

        List<TbProtoQueueMsg<ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4);
        HistoricalStatsTotalAccumulator.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<ToUsageStatsMsgProto> msg : msgs) {
            results = accumulator.calculatePairUsingProvidedMsg(msg.getValue());
        }

        assertEquals(43, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    void givenStatsMsg_whenReceiveOutgoingMessage_thenCalculateTsTotalStatsForOutgoingMsgs() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        var msg1 = buildMessage(OUTGOING_MSGS, 5);
        var msg2 = buildMessage(OUTGOING_MSGS, 10);
        var msg3 = buildMessage(OUTGOING_MSGS, 25);
        var msg4 = buildMessage(OUTGOING_MSGS, 3);

        List<TbProtoQueueMsg<ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4);
        HistoricalStatsTotalAccumulator.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<ToUsageStatsMsgProto> msg : msgs) {
            results = accumulator.calculatePairUsingProvidedMsg(msg.getValue());
        }

        assertEquals(43, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    void givenStatsMsg_whenReceiveDroppedMessage_thenCalculateTsTotalStatsForDroppedMsgs() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        var msg1 = buildMessage(DROPPED_MSGS, 5);
        var msg2 = buildMessage(DROPPED_MSGS, 10);
        var msg3 = buildMessage(DROPPED_MSGS, 25);
        var msg4 = buildMessage(DROPPED_MSGS, 3);

        List<TbProtoQueueMsg<ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4);
        HistoricalStatsTotalAccumulator.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<ToUsageStatsMsgProto> msg : msgs) {
            results = accumulator.calculatePairUsingProvidedMsg(msg.getValue());
        }

        assertEquals(43, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    void givenStatsMsg_whenReceiveNextTs_thenCalculateForNextTotalTs() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        int nextMsgTs = 100;

        var msg1 = buildMessage(INCOMING_MSGS, 5);
        var msg2 = buildMessage(INCOMING_MSGS, 10);
        var msg3 = buildMessage(INCOMING_MSGS, 25);
        var msg4 = buildMessageWithDifferentTs(INCOMING_MSGS, 10, nextMsgTs);
        var msg5 = buildMessageWithDifferentTs(INCOMING_MSGS, 13, nextMsgTs);

        List<TbProtoQueueMsg<ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4, msg5);
        HistoricalStatsTotalAccumulator.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<ToUsageStatsMsgProto> msg : msgs) {
            results = accumulator.calculatePairUsingProvidedMsg(msg.getValue());
        }

        assertEquals(23, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    void givenStatsMsg_whenReceivePreviousTs_thenSkipStaleMessages() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        int nextTsMsg = -100;

        var msg1 = buildMessage(OUTGOING_MSGS, 5);
        var msg2 = buildMessage(OUTGOING_MSGS, 10);
        var msg3 = buildMessage(OUTGOING_MSGS, 25);
        var msg4 = buildMessageWithDifferentTs(OUTGOING_MSGS, 10, nextTsMsg);
        var msg5 = buildMessageWithDifferentTs(OUTGOING_MSGS, 13, nextTsMsg);

        List<TbProtoQueueMsg<ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4, msg5);
        HistoricalStatsTotalAccumulator.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<ToUsageStatsMsgProto> msg : msgs) {
            results = accumulator.calculatePairUsingProvidedMsg(msg.getValue());
        }

        assertEquals(40, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    void givenStatsMsgs_thenCalculateTotalForEachKey() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        var msg1 = buildMessage(DROPPED_MSGS, 5);
        var msg2 = buildMessage(DROPPED_MSGS, 13);
        var msg3 = buildMessage(DROPPED_MSGS, 6);
        var msg4 = buildMessage(INCOMING_MSGS, 10);
        var msg5 = buildMessage(INCOMING_MSGS, 3);
        var msg6 = buildMessage(INCOMING_MSGS, 3);
        var msg7 = buildMessage(INCOMING_MSGS, 11);
        var msg8 = buildMessage(OUTGOING_MSGS, 25);
        var msg9 = buildMessage(OUTGOING_MSGS, 11);

        Map<String, HistoricalStatsTotalAccumulator.TsMsgTotalPair> tsMsgTotalPairMap = new HashMap<>();

        List<TbProtoQueueMsg<ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4, msg5, msg6, msg7, msg8, msg9);

        for (TbProtoQueueMsg<ToUsageStatsMsgProto> msg : msgs) {
            String key = msg.getValue().getUsageStats().getKey();
            tsMsgTotalPairMap.put(key, accumulator.calculatePairUsingProvidedMsg(msg.getValue()));
        }

        tsMsgTotalPairMap.forEach((key, value) -> {
            if (INCOMING_MSGS.equals(key)) {
                assertEquals(27, value.getTotalMsgCounter());
            } else if (OUTGOING_MSGS.equals(key)) {
                assertEquals(36, value.getTotalMsgCounter());
            } else {
                assertEquals(24, value.getTotalMsgCounter());
            }
        });
        verify(timeseriesService, times(3)).findLatest(any(), any());
    }

    @Test
    void givenStatsMsgs_whenNextTsComes_thenCalculateNewTotalForEachKey() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        int nextTsMsg = 100;

        var msg1 = buildMessage(DROPPED_MSGS, 5);
        var msg2 = buildMessage(DROPPED_MSGS, 13);
        var msg3 = buildMessageWithDifferentTs(DROPPED_MSGS, 6, nextTsMsg);
        var msg4 = buildMessage(INCOMING_MSGS, 10);
        var msg5 = buildMessage(INCOMING_MSGS, 3);
        var msg6 = buildMessageWithDifferentTs(INCOMING_MSGS, 3, nextTsMsg);
        var msg7 = buildMessageWithDifferentTs(INCOMING_MSGS, 11, nextTsMsg);
        var msg8 = buildMessage(OUTGOING_MSGS, 25);
        var msg9 = buildMessageWithDifferentTs(OUTGOING_MSGS, 11, -nextTsMsg);

        Map<String, HistoricalStatsTotalAccumulator.TsMsgTotalPair> tsMsgTotalPairMap = new HashMap<>();

        List<TbProtoQueueMsg<ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4, msg5, msg6, msg7, msg8, msg9);

        for (TbProtoQueueMsg<ToUsageStatsMsgProto> msg : msgs) {
            String key = msg.getValue().getUsageStats().getKey();
            tsMsgTotalPairMap.put(key, accumulator.calculatePairUsingProvidedMsg(msg.getValue()));
        }

        tsMsgTotalPairMap.forEach((key, value) -> {
            if (INCOMING_MSGS.equals(key)) {
                assertEquals(14, value.getTotalMsgCounter());
            } else if (OUTGOING_MSGS.equals(key)) {
                assertEquals(25, value.getTotalMsgCounter());
            } else {
                assertEquals(6, value.getTotalMsgCounter());
            }
        });
        verify(timeseriesService, times(3)).findLatest(any(), any());
    }

    @Test
    void givenMsgWithUnknownRemovedKey_whenAccumulate_thenIgnoredWithoutLookupOrNpe() {
        // processedBytes was removed from the historical key set, so the accumulator has no entry for it.
        // A message with such a key (e.g. produced by a not-yet-upgraded node during a rolling upgrade)
        // must be ignored, not throw NPE — otherwise the uncommitted batch is re-polled forever and
        // poisons cluster-wide historical aggregation.
        assertThat(accumulator.accumulate(buildMessage(PROCESSED_BYTES, 7).getValue())).isNull();

        verify(timeseriesService, never()).findLatest(any(), any());
    }

    @Test
    void givenMsgsOfOneInterval_whenAccumulate_thenEachReturnsTheRunningTotalAtTheIntervalTs() {
        when(timeseriesService.findLatest(any(), any())).thenReturn(Futures.immediateFuture(new ArrayList<>()));

        accumulator.accumulate(buildMessage(INCOMING_MSGS, 5).getValue());
        TsKvEntry entry = accumulator.accumulate(buildMessage(INCOMING_MSGS, 7).getValue());

        assertThat(entry.getKey()).isEqualTo(INCOMING_MSGS);
        assertThat(entry.getTs()).isEqualTo(ts);
        assertThat(entry.getLongValue()).contains(12L);
    }

    @Test
    void givenMsgOlderThanTheHeldInterval_whenAccumulate_thenItIsReturnedAtItsOwnTsAndTheHeldTotalIsKept() {
        when(timeseriesService.findLatest(any(), any())).thenReturn(Futures.immediateFuture(new ArrayList<>()));
        accumulator.accumulate(buildMessage(INCOMING_MSGS, 5).getValue());

        TsKvEntry older = accumulator.accumulate(buildMessageWithDifferentTs(INCOMING_MSGS, 9, -100).getValue());
        TsKvEntry current = accumulator.accumulate(buildMessage(INCOMING_MSGS, 1).getValue());

        assertThat(older.getTs()).isEqualTo(ts - 100);
        assertThat(older.getLongValue()).contains(9L);
        assertThat(current.getTs()).isEqualTo(ts);
        assertThat(current.getLongValue()).contains(6L);
    }

    private TbProtoQueueMsg<ToUsageStatsMsgProto> buildMessage(String key, int value) {

        UsageStatsKVProto statsItem = UsageStatsKVProto.newBuilder()
                .setKey(key)
                .setValue(value)
                .build();

        ToUsageStatsMsgProto statsMsg = ToUsageStatsMsgProto.newBuilder()
                .setTs(ts)
                .setUsageStats(statsItem)
                .build();

        return new TbProtoQueueMsg<>(statsMsg);
    }

    private TbProtoQueueMsg<ToUsageStatsMsgProto> buildMessageWithDifferentTs(String key, int value, int updateTs) {

        UsageStatsKVProto statsItem = UsageStatsKVProto.newBuilder()
                .setKey(key)
                .setValue(value)
                .build();

        ToUsageStatsMsgProto statsMsg = ToUsageStatsMsgProto.newBuilder()
                .setTs(ts + updateTs)
                .setUsageStats(statsItem)
                .build();

        return new TbProtoQueueMsg<>(statsMsg);
    }

}
