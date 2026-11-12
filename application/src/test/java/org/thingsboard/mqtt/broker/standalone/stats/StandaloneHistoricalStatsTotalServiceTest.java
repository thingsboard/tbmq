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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.gen.queue.UsageStatsKVProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandaloneHistoricalStatsTotalServiceTest {

    @Mock
    private ClientSessionService clientSessionService;
    @Mock
    private ClientSubscriptionService clientSubscriptionService;
    @Mock
    private RetainedMsgListenerService retainedMsgService;
    @Mock
    private TimeseriesService timeseriesService;
    @Mock
    private HistoricalDataReportProperties historicalDataReportProperties;
    @Mock
    private TbQueueCallback callback;

    private StandaloneHistoricalStatsTotalService service;

    @BeforeEach
    void setUp() {
        service = new StandaloneHistoricalStatsTotalService(clientSessionService, clientSubscriptionService,
                retainedMsgService, timeseriesService, historicalDataReportProperties);
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    void givenEnabled_whenPublish_thenOneTotalEntryIsSavedWithTheProtoTsKeyAndValueThenOnSuccess() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        when(timeseriesService.save(eq(BrokerConstants.ENTITY_ID_TOTAL), any(TsKvEntry.class)))
                .thenReturn(Futures.immediateFuture(null));
        service.init();

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
        service.init();

        service.publish(usageMsg("incomingMsgs", 7L, 1000L), callback);

        verify(callback).onFailure(cause);
        verify(callback, never()).onSuccess(any());
    }

    @Test
    void givenDisabled_whenPublishAndProcess_thenNothingIsSaved() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(true);
        service.init();

        service.publish(usageMsg("incomingMsgs", 7L, 1000L), callback);
        service.process();

        verifyNoInteractions(timeseriesService, clientSessionService, clientSubscriptionService, retainedMsgService, callback);
    }

    @Test
    void givenServicesInitialised_whenProcess_thenSessionsSubscriptionsAndRetainedMsgsAreSavedUnderTotal() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        when(clientSessionService.isInitialized()).thenReturn(true);
        when(clientSubscriptionService.isInitialized()).thenReturn(true);
        when(retainedMsgService.isInitialized()).thenReturn(true);
        when(clientSessionService.getClientSessionsCount()).thenReturn(3);
        when(clientSubscriptionService.getClientSubscriptionsCount()).thenReturn(5L);
        when(retainedMsgService.getRetainedMessagesCount()).thenReturn(2);
        when(timeseriesService.save(eq(BrokerConstants.ENTITY_ID_TOTAL), anyList()))
                .thenReturn(Futures.immediateFuture(null));
        service.init();

        service.process();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TsKvEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(timeseriesService, timeout(5000)).save(eq(BrokerConstants.ENTITY_ID_TOTAL), captor.capture());
        Map<String, TsKvEntry> byKey = captor.getValue().stream().collect(Collectors.toMap(TsKvEntry::getKey, Function.identity()));
        assertThat(byKey).containsOnlyKeys(BrokerConstants.SESSIONS, BrokerConstants.SUBSCRIPTIONS, BrokerConstants.RETAINED_MSGS);
        assertThat(byKey.get(BrokerConstants.SESSIONS).getLongValue()).contains(3L);
        assertThat(byKey.get(BrokerConstants.SUBSCRIPTIONS).getLongValue()).contains(5L);
        assertThat(byKey.get(BrokerConstants.RETAINED_MSGS).getLongValue()).contains(2L);
        assertThat(byKey.values().stream().map(TsKvEntry::getTs).distinct()).hasSize(1);
    }

    @Test
    void givenAServiceNotInitialised_whenProcess_thenNothingIsWritten() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        when(clientSessionService.isInitialized()).thenReturn(true);
        when(clientSubscriptionService.isInitialized()).thenReturn(false);
        service.init();

        service.process();

        verify(timeseriesService, after(300).never()).save(anyString(), anyList());
        verify(clientSessionService, never()).getClientSessionsCount();
    }

    private static ToUsageStatsMsgProto usageMsg(String key, long value, long ts) {
        return ToUsageStatsMsgProto.newBuilder()
                .setServiceId("service-1")
                .setTs(ts)
                .setUsageStats(UsageStatsKVProto.newBuilder().setKey(key).setValue(value).build())
                .build();
    }

}
