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
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalStatsCountsReporterTest {

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

    private HistoricalStatsCountsReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new HistoricalStatsCountsReporter(clientSessionService, clientSubscriptionService,
                retainedMsgService, timeseriesService, historicalDataReportProperties);
    }

    @AfterEach
    void tearDown() {
        reporter.destroy();
    }

    @Test
    void givenServicesInitialised_whenProcess_thenSessionsSubscriptionsAndRetainedMsgsAreSavedUnderTotal() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        allInitialised();
        when(clientSessionService.getClientSessionsCount()).thenReturn(3);
        when(clientSubscriptionService.getClientSubscriptionsCount()).thenReturn(5L);
        when(retainedMsgService.getRetainedMessagesCount()).thenReturn(2);
        when(timeseriesService.save(eq(BrokerConstants.ENTITY_ID_TOTAL), anyList()))
                .thenReturn(Futures.immediateFuture(null));
        reporter.init();

        reporter.process();

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
    void givenAServiceNotInitialised_whenProcess_thenNothingIsCountedOrWritten() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(false);
        when(clientSessionService.isInitialized()).thenReturn(true);
        when(clientSubscriptionService.isInitialized()).thenReturn(false);
        when(retainedMsgService.isInitialized()).thenReturn(true);
        reporter.init();

        // the readiness check runs on the calling thread and nothing is handed to the executor
        reporter.process();

        verify(clientSessionService, never()).getClientSessionsCount();
        verifyNoInteractions(timeseriesService);
    }

    @Test
    void givenDisabled_whenProcess_thenNothingIsRead() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(true);
        reporter.init();

        reporter.process();

        verifyNoInteractions(timeseriesService, clientSessionService, clientSubscriptionService, retainedMsgService);
    }

    private void allInitialised() {
        when(clientSessionService.isInitialized()).thenReturn(true);
        when(clientSubscriptionService.isInitialized()).thenReturn(true);
        when(retainedMsgService.isInitialized()).thenReturn(true);
    }

}
