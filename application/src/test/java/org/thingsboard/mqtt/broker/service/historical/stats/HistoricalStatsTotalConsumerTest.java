/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.DROPPED_MSGS;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.INCOMING_MSGS;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.MSG_RELATED_HISTORICAL_KEYS;
import static org.thingsboard.mqtt.broker.common.util.BrokerConstants.OUTGOING_MSGS;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class HistoricalStatsTotalConsumerTest {

    private TimeseriesService timeseriesService;

    private HistoricalStatsTotalConsumer historicalStatsTotalConsumer;

    private long ts;

    @Before
    public void init() {
        ts = System.currentTimeMillis();
        timeseriesService = mock(TimeseriesService.class);
        historicalStatsTotalConsumer = spy(new HistoricalStatsTotalConsumer(
                null, null, null, null, null, timeseriesService
        ));
        historicalStatsTotalConsumer.setTotalStatsMap(initAndGetTotalMessageMap());
    }

    @Test
    public void givenStatsMsg_whenReceiveIncomingMessage_thenCalculateTsTotalStatsForIncomingMsgs() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        var msg1 = buildMessage(INCOMING_MSGS, 5);
        var msg2 = buildMessage(INCOMING_MSGS, 10);
        var msg3 = buildMessage(INCOMING_MSGS, 25);
        var msg4 = buildMessage(INCOMING_MSGS, 3);

        List<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4);
        HistoricalStatsTotalConsumer.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg : msgs) {
            results = historicalStatsTotalConsumer.calculatePairUsingProvidedMsg(msg);
        }

        Assert.assertEquals(43, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    public void givenStatsMsg_whenReceiveOutgoingMessage_thenCalculateTsTotalStatsForOutgoingMsgs() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        var msg1 = buildMessage(OUTGOING_MSGS, 5);
        var msg2 = buildMessage(OUTGOING_MSGS, 10);
        var msg3 = buildMessage(OUTGOING_MSGS, 25);
        var msg4 = buildMessage(OUTGOING_MSGS, 3);

        List<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4);
        HistoricalStatsTotalConsumer.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg : msgs) {
            results = historicalStatsTotalConsumer.calculatePairUsingProvidedMsg(msg);
        }

        Assert.assertEquals(43, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    public void givenStatsMsg_whenReceiveDroppedMessage_thenCalculateTsTotalStatsForDroppedMsgs() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        var msg1 = buildMessage(DROPPED_MSGS, 5);
        var msg2 = buildMessage(DROPPED_MSGS, 10);
        var msg3 = buildMessage(DROPPED_MSGS, 25);
        var msg4 = buildMessage(DROPPED_MSGS, 3);

        List<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4);
        HistoricalStatsTotalConsumer.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg : msgs) {
            results = historicalStatsTotalConsumer.calculatePairUsingProvidedMsg(msg);
        }

        Assert.assertEquals(43, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    public void givenStatsMsg_whenReceiveNextTs_thenCalculateForNextTotalTs() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        int nextMsgTs = 100;

        var msg1 = buildMessage(INCOMING_MSGS, 5);
        var msg2 = buildMessage(INCOMING_MSGS, 10);
        var msg3 = buildMessage(INCOMING_MSGS, 25);
        var msg4 = buildMessageWithDifferentTs(INCOMING_MSGS, 10, nextMsgTs);
        var msg5 = buildMessageWithDifferentTs(INCOMING_MSGS, 13, nextMsgTs);

        List<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4, msg5);
        HistoricalStatsTotalConsumer.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg : msgs) {
            results = historicalStatsTotalConsumer.calculatePairUsingProvidedMsg(msg);
        }

        Assert.assertEquals(23, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    public void givenStatsMsg_whenReceivePreviousTs_thenCalculateForCurrentTotalTs() {
        List<TsKvEntry> entries = new ArrayList<>();
        var futuresEntry = Futures.immediateFuture(entries);
        when(timeseriesService.findLatest(any(), any())).thenReturn(futuresEntry);

        int nextTsMsg = -100;

        var msg1 = buildMessage(OUTGOING_MSGS, 5);
        var msg2 = buildMessage(OUTGOING_MSGS, 10);
        var msg3 = buildMessage(OUTGOING_MSGS, 25);
        var msg4 = buildMessageWithDifferentTs(OUTGOING_MSGS, 10, nextTsMsg);
        var msg5 = buildMessageWithDifferentTs(OUTGOING_MSGS, 13, nextTsMsg);

        List<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4, msg5);
        HistoricalStatsTotalConsumer.TsMsgTotalPair results = null;

        for (TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg : msgs) {
            results = historicalStatsTotalConsumer.calculatePairUsingProvidedMsg(msg);
        }

        Assert.assertEquals(63, results.getTotalMsgCounter());
        verify(timeseriesService, times(1)).findLatest(any(), any());
    }

    @Test
    public void givenStatsMsgs_thenCalculateTotalForEachKey() {
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

        Map<String, HistoricalStatsTotalConsumer.TsMsgTotalPair> tsMsgTotalPairMap = new HashMap<>();

        List<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4, msg5, msg6, msg7, msg8, msg9);

        for (TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg : msgs) {
            String key = msg.getValue().getUsageStats().getKey();
            tsMsgTotalPairMap.put(key, historicalStatsTotalConsumer.calculatePairUsingProvidedMsg(msg));
        }

        tsMsgTotalPairMap.forEach((key, value) -> {
            if (INCOMING_MSGS.equals(key)) {
                Assert.assertEquals(27, value.getTotalMsgCounter());
            } else if (OUTGOING_MSGS.equals(key)) {
                Assert.assertEquals(36, value.getTotalMsgCounter());
            } else {
                Assert.assertEquals(24, value.getTotalMsgCounter());
            }
        });
        verify(timeseriesService, times(3)).findLatest(any(), any());
    }

    @Test
    public void givenStatsMsgs_whenNextTsComes_thenCalculateNewTotalForEachKey() {
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

        Map<String, HistoricalStatsTotalConsumer.TsMsgTotalPair> tsMsgTotalPairMap = new HashMap<>();

        List<TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto>> msgs = List.of(msg1, msg2, msg3, msg4, msg5, msg6, msg7, msg8, msg9);

        for (TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> msg : msgs) {
            String key = msg.getValue().getUsageStats().getKey();
            tsMsgTotalPairMap.put(key, historicalStatsTotalConsumer.calculatePairUsingProvidedMsg(msg));
        }

        tsMsgTotalPairMap.forEach((key, value) -> {
            if (INCOMING_MSGS.equals(key)) {
                Assert.assertEquals(14, value.getTotalMsgCounter());
            } else if (OUTGOING_MSGS.equals(key)) {
                Assert.assertEquals(36, value.getTotalMsgCounter());
            } else {
                Assert.assertEquals(6, value.getTotalMsgCounter());
            }
        });
        verify(timeseriesService, times(3)).findLatest(any(), any());
    }

    private TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> buildMessage(String key, int value) {

        QueueProtos.UsageStatsKVProto statsItem = QueueProtos.UsageStatsKVProto.newBuilder()
                .setKey(key)
                .setValue(value)
                .build();

        QueueProtos.ToUsageStatsMsgProto statsMsg = QueueProtos.ToUsageStatsMsgProto.newBuilder()
                .setTs(ts)
                .setUsageStats(statsItem)
                .build();

        return new TbProtoQueueMsg<>(statsMsg);
    }

    private TbProtoQueueMsg<QueueProtos.ToUsageStatsMsgProto> buildMessageWithDifferentTs(String key, int value, int updateTs) {

        QueueProtos.UsageStatsKVProto statsItem = QueueProtos.UsageStatsKVProto.newBuilder()
                .setKey(key)
                .setValue(value)
                .build();

        QueueProtos.ToUsageStatsMsgProto statsMsg = QueueProtos.ToUsageStatsMsgProto.newBuilder()
                .setTs(ts + updateTs)
                .setUsageStats(statsItem)
                .build();

        return new TbProtoQueueMsg<>(statsMsg);
    }

    private Map<String, HistoricalStatsTotalConsumer.TsMsgTotalPair> initAndGetTotalMessageMap() {
        Map<String, HistoricalStatsTotalConsumer.TsMsgTotalPair> totalMessageCounter = new HashMap<>();
        for (String key : MSG_RELATED_HISTORICAL_KEYS) {
            totalMessageCounter.put(key, new HistoricalStatsTotalConsumer.TsMsgTotalPair());
        }
        return totalMessageCounter;
    }

}
