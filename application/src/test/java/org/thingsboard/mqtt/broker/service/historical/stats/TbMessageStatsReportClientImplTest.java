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
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.HistoricalDataQueueFactory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TbMessageStatsReportClientImplTest {

    @Mock
    private HistoricalDataQueueFactory historicalDataQueueFactory;
    @Mock
    private ServiceInfoProvider serviceInfoProvider;
    @Mock
    private TimeseriesService timeseriesService;
    @Mock
    private HistoricalStatsTotalHelper helper;
    @Mock
    private HistoricalDataReportProperties historicalDataReportProperties;
    @Mock
    private TbQueueProducer<TbProtoQueueMsg<ToUsageStatsMsgProto>> historicalStatsProducer;

    @InjectMocks
    private TbMessageStatsReportClientImpl tbMessageStatsReportClient;

    AutoCloseable autoCloseable;

    @Before
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);

        when(serviceInfoProvider.getServiceId()).thenReturn("service-1");
        when(historicalDataQueueFactory.createProducer(any())).thenReturn(historicalStatsProducer);

        when(historicalDataReportProperties.isEnabled()).thenReturn(true);
        when(historicalDataReportProperties.getInterval()).thenReturn(1);
    }

    @After
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testInitEnabled() {
        tbMessageStatsReportClient.init();

        assertNotNull(tbMessageStatsReportClient.getStats());
        assertNotNull(tbMessageStatsReportClient.getClientSessionsStats());
        assertEquals("service-1", tbMessageStatsReportClient.getServiceId());
        verify(historicalDataQueueFactory, times(1)).createProducer("service-1");
    }

    @Test
    public void testInitDisabled() {
        when(historicalDataReportProperties.isDisabled()).thenReturn(true);
        tbMessageStatsReportClient.init();

        assertNull(tbMessageStatsReportClient.getStats());
        assertNull(tbMessageStatsReportClient.getClientSessionsStats());
        verify(historicalDataQueueFactory, never()).createProducer(any());
    }

    @Test
    public void testReportClientSendStats() {
        tbMessageStatsReportClient.init();

        tbMessageStatsReportClient.reportClientSendStats("client-1", 1);

        assertTrue(tbMessageStatsReportClient.getClientSessionsStats().containsKey("client-1"));
        assertTrue(tbMessageStatsReportClient.getClientSessionsStats().get("client-1").containsKey(BrokerConstants.SENT_PUBLISH_MSGS));
        assertTrue(tbMessageStatsReportClient.getClientSessionsStats().get("client-1").containsKey(BrokerConstants.getQosSentStatsKey(1)));
    }

    @Test
    public void testReportClientReceiveStats() {
        tbMessageStatsReportClient.init();

        tbMessageStatsReportClient.reportClientReceiveStats("client-1", 2);

        assertTrue(tbMessageStatsReportClient.getClientSessionsStats().containsKey("client-1"));
        assertTrue(tbMessageStatsReportClient.getClientSessionsStats().get("client-1").containsKey(BrokerConstants.RECEIVED_PUBLISH_MSGS));
        assertTrue(tbMessageStatsReportClient.getClientSessionsStats().get("client-1").containsKey(BrokerConstants.getQosReceivedStatsKey(2)));
    }

    @Test
    public void testReportStats() {
        tbMessageStatsReportClient.init();

        String key = "test-key";
        tbMessageStatsReportClient.getStats().put(key, new AtomicLong(0));

        tbMessageStatsReportClient.reportStats(key);

        assertEquals(1, tbMessageStatsReportClient.getStats().get(key).get(), "The counter should be incremented by 1");
    }

    @Test
    public void testReportInboundTraffic() {
        tbMessageStatsReportClient.init();

        tbMessageStatsReportClient.getStats().put(BrokerConstants.INBOUND_PAYLOAD_BYTES, new AtomicLong(0));

        tbMessageStatsReportClient.reportInboundTraffic(1024);

        assertEquals(1024, tbMessageStatsReportClient.getStats().get(BrokerConstants.INBOUND_PAYLOAD_BYTES).get());
    }

    @Test
    public void testReportOutboundTraffic() {
        tbMessageStatsReportClient.init();

        tbMessageStatsReportClient.getStats().put(BrokerConstants.OUTBOUND_PAYLOAD_BYTES, new AtomicLong(0));

        tbMessageStatsReportClient.reportOutBoundTraffic(1024);

        assertEquals(1024, tbMessageStatsReportClient.getStats().get(BrokerConstants.OUTBOUND_PAYLOAD_BYTES).get());
    }

    @Test
    public void testRemoveClient() {
        tbMessageStatsReportClient.init();

        tbMessageStatsReportClient.getClientSessionsStats().put("client-1", new ConcurrentHashMap<>());

        tbMessageStatsReportClient.removeClient("client-1");

        assertFalse(tbMessageStatsReportClient.getClientSessionsStats().containsKey("client-1"));
    }

    @Test
    public void testReportClientSessionsStatsWithChangedMetrics() {
        tbMessageStatsReportClient.init();

        // Given
        String clientId = "client-1";
        String key = "sent-messages";
        long timestamp = System.currentTimeMillis();
        AtomicLong counter = new AtomicLong(10);

        // Setting up a client session with changed stats
        ClientSessionMetricState metricState = new ClientSessionMetricState(counter, new AtomicBoolean(true));
        ConcurrentMap<String, ClientSessionMetricState> clientStatsMap = new ConcurrentHashMap<>();
        clientStatsMap.put(key, metricState);
        tbMessageStatsReportClient.getClientSessionsStats().put(clientId, clientStatsMap);

        // Mocking timeseries service response
        ListenableFuture<List<Void>> mockFuture = Futures.immediateFuture(new ArrayList<>());
        when(timeseriesService.saveLatest(eq(clientId), anyList())).thenReturn(mockFuture);

        // When
        tbMessageStatsReportClient.reportClientSessionsStats(timestamp);

        // Then
        verify(timeseriesService, times(1)).saveLatest(eq(clientId), anyList());
        assertFalse(metricState.getValueChangedSinceLastUpdate().get(), "Metric valueChangedSinceLastUpdate should be reset to false.");
    }

    @Test
    public void testReportClientSessionsStatsWithoutChangedMetrics() {
        tbMessageStatsReportClient.init();

        // Given
        String clientId = "client-2";
        String key = "sent-messages";
        long timestamp = System.currentTimeMillis();
        AtomicLong counter = new AtomicLong(20);

        // Setting up a client session without changed stats
        ClientSessionMetricState metricState = new ClientSessionMetricState(counter, new AtomicBoolean(false));
        ConcurrentMap<String, ClientSessionMetricState> clientStatsMap = new ConcurrentHashMap<>();
        clientStatsMap.put(key, metricState);
        tbMessageStatsReportClient.getClientSessionsStats().put(clientId, clientStatsMap);

        // When
        tbMessageStatsReportClient.reportClientSessionsStats(timestamp);

        // Then
        verify(timeseriesService, never()).saveLatest(anyString(), anyList());
    }

    // --- calculateNextCronBoundary tests ---

    @Test
    public void testNextCronBoundary_interval1_alwaysNextMinute() {
        long minute0 = minuteMs(10, 0);
        assertEquals(minuteMs(10, 1), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minute0, 1));

        long minute30 = minuteMs(10, 30);
        assertEquals(minuteMs(10, 31), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minute30, 1));

        long minute59 = minuteMs(10, 59);
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minute59, 1));
    }

    @Test
    public void testNextCronBoundary_interval3_onBoundary() {
        // At minute 0 → next is minute 3
        assertEquals(minuteMs(10, 3), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 0), 3));
        // At minute 3 → next is minute 6
        assertEquals(minuteMs(10, 6), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 3), 3));
        // At minute 57 → next is minute 60 (i.e. next hour :00)
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 57), 3));
    }

    @Test
    public void testNextCronBoundary_interval3_betweenBoundaries() {
        // At minute 1 → next boundary is minute 3
        assertEquals(minuteMs(10, 3), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 1), 3));
        // At minute 2 → next boundary is minute 3
        assertEquals(minuteMs(10, 3), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 2), 3));
        // At minute 4 → next boundary is minute 6
        assertEquals(minuteMs(10, 6), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 4), 3));
        // At minute 58 → next boundary is minute 60 (next hour :00)
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 58), 3));
    }

    @Test
    public void testNextCronBoundary_interval5() {
        assertEquals(minuteMs(10, 5), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 0), 5));
        assertEquals(minuteMs(10, 5), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 3), 5));
        assertEquals(minuteMs(10, 10), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 5), 5));
        assertEquals(minuteMs(10, 60), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 55), 5));
    }

    @Test
    public void testNextCronBoundary_interval15() {
        assertEquals(minuteMs(10, 15), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 0), 15));
        assertEquals(minuteMs(10, 15), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 7), 15));
        assertEquals(minuteMs(10, 30), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 15), 15));
        assertEquals(minuteMs(10, 60), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 45), 15));
    }

    @Test
    public void testNextCronBoundary_interval30() {
        assertEquals(minuteMs(10, 30), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 0), 30));
        assertEquals(minuteMs(10, 30), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 14), 30));
        assertEquals(minuteMs(10, 60), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 30), 30));
    }

    @Test
    public void testNextCronBoundary_interval7_nonDivisorOf60() {
        // Cron 0/7 fires at: 0, 7, 14, 21, 28, 35, 42, 49, 56. After 56 → wraps to next hour :00.
        assertEquals(minuteMs(10, 7), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 0), 7));
        assertEquals(minuteMs(10, 7), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 3), 7));
        assertEquals(minuteMs(10, 14), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 7), 7));
        assertEquals(minuteMs(10, 56), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 49), 7));
        // After minute 56, wraps to next hour :00 (not :63)
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 56), 7));
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 58), 7));
    }

    @Test
    public void testNextCronBoundary_interval8_nonDivisorOf60() {
        // Cron 0/8 fires at: 0, 8, 16, 24, 32, 40, 48, 56. After 56 → wraps to next hour :00.
        assertEquals(minuteMs(10, 8), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 0), 8));
        assertEquals(minuteMs(10, 56), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 48), 8));
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 56), 8));
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 59), 8));
    }

    @Test
    public void testNextCronBoundary_interval60() {
        // Only fires at :00, so next is always the next hour
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 0), 60));
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 30), 60));
        assertEquals(minuteMs(11, 0), TbMessageStatsReportClientImpl.calculateNextCronBoundary(minuteMs(10, 59), 60));
    }

    private static long minuteMs(int hour, int minute) {
        return LocalDateTime.of(2026, 3, 25, hour, 0)
                .plusMinutes(minute)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
    }

    @Test
    public void testReportAndPersistStats() {
        tbMessageStatsReportClient.init();

        // Given
        long timestamp = System.currentTimeMillis();
        tbMessageStatsReportClient.getStats().get(BrokerConstants.INCOMING_MSGS).set(100);
        tbMessageStatsReportClient.getStats().get(BrokerConstants.OUTGOING_MSGS).set(200);

        // Mock futures for saveLatest and queue send
        ListenableFuture<Void> mockFuture = Futures.immediateFuture(null);
        when(timeseriesService.save(anyString(), any(BasicTsKvEntry.class))).thenReturn(mockFuture);
        when(helper.getTopic()).thenReturn("topic");

        // When
        tbMessageStatsReportClient.reportAndPersistStats(timestamp, null);

        // Then
        verify(timeseriesService, times(6)).save(anyString(), any(BasicTsKvEntry.class));
        verify(historicalStatsProducer, times(6)).send(eq("topic"), eq(null), any(TbProtoQueueMsg.class), any(TbQueueCallback.class));

        // Assert that stats are reset to 0
        assertEquals(0, tbMessageStatsReportClient.getStats().get(BrokerConstants.INCOMING_MSGS).get());
        assertEquals(0, tbMessageStatsReportClient.getStats().get(BrokerConstants.OUTGOING_MSGS).get());
    }

}
