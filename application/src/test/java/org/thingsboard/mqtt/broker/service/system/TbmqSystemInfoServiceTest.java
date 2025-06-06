/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.system;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.gen.queue.SystemInfoProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.cache.CacheConstants.SERVICE_REGISTRY_KEY;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_COUNT;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DISK_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_DISK_SPACE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_MEMORY;
import static org.thingsboard.mqtt.broker.common.data.queue.ServiceType.TBMQ;

public class TbmqSystemInfoServiceTest {

    @Mock
    private ServiceInfoProvider serviceInfoProvider;
    @Mock
    private TimeseriesService timeseriesService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private TbmqSystemInfoService systemInfoService;

    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        systemInfoService = new TbmqSystemInfoService(serviceInfoProvider, timeseriesService, redisTemplate);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testSaveCurrentServiceInfo() {
        ServiceInfo serviceInfo = ServiceInfo.newBuilder().setServiceId("test-service").build();
        when(serviceInfoProvider.getServiceInfo()).thenReturn(serviceInfo);
        when(timeseriesService.saveLatest(eq("test-service"), anyList())).thenReturn(Futures.immediateFuture(null));

        systemInfoService.saveCurrentServiceInfo();

        verify(timeseriesService).saveLatest(eq("test-service"), anyList());
    }

    @Test
    public void testProcessIeServiceInfoWithoutSystemInfo() {
        ServiceInfo serviceInfo = ServiceInfo
                .newBuilder()
                .setServiceId("svc1")
                .setServiceType("type1")
                .build();

        systemInfoService.processIeServiceInfo(serviceInfo);

        verify(hashOperations).put(SERVICE_REGISTRY_KEY, "svc1", "type1");
    }

    @Test
    public void testProcessIeServiceInfoWithSystemInfo() {
        when(timeseriesService.saveLatest(eq("svc2"), anyList())).thenReturn(Futures.immediateFuture(null));

        ServiceInfo serviceInfo = ServiceInfo
                .newBuilder()
                .setServiceId("svc2")
                .setServiceType("type1")
                .setSystemInfo(SystemInfoProto
                        .newBuilder()
                        .setCpuCount(4)
                        .setCpuUsage(10)
                        .setDiskUsage(50)
                        .setTotalDiskSpace(100000)
                        .setMemoryUsage(200)
                        .setTotalMemory(8192)
                        .build())
                .build();

        systemInfoService.processIeServiceInfo(serviceInfo);

        verify(timeseriesService).saveLatest(eq("svc2"), anyList());
    }

    @Test
    public void testRemoveServiceInfoExists() {
        when(hashOperations.delete(SERVICE_REGISTRY_KEY, "svc3")).thenReturn(1L);
        assertDoesNotThrow(() -> systemInfoService.removeServiceInfo("svc3"));
    }

    @Test
    public void testRemoveServiceInfoNotFound() {
        when(hashOperations.delete(SERVICE_REGISTRY_KEY, "svc4")).thenReturn(0L);
        assertThrows(ThingsboardException.class, () -> systemInfoService.removeServiceInfo("svc4"));
    }

    @Test
    public void testGetServiceInfosWhenEmpty() {
        when(hashOperations.entries(SERVICE_REGISTRY_KEY)).thenReturn(Map.of());

        assertThrows(ThingsboardException.class, () -> systemInfoService.getServiceInfos().get());
    }

    @Test
    public void testGetServiceInfos() throws Exception {
        String serviceId = "tbmq";
        String serviceType = TBMQ.name();

        when(hashOperations.entries(SERVICE_REGISTRY_KEY)).thenReturn(Map.of(serviceId, serviceType));

        long ts = System.currentTimeMillis();
        SettableFuture<List<TsKvEntry>> future = getListSettableFuture(ts);
        when(timeseriesService.findLatest(eq(serviceId), anyList())).thenReturn(future);

        var resultFuture = systemInfoService.getServiceInfos();
        PageData<ServiceInfoDto> result = resultFuture.get();

        assertEquals(1, result.getData().size());
        ServiceInfoDto dto = result.getData().get(0);
        assertEquals(serviceId, dto.getServiceId());
        assertEquals(serviceType, dto.getServiceType());
        assertEquals(10L, dto.getCpuUsage());
        assertEquals(1000L, dto.getMemoryUsage());
        assertEquals(500L, dto.getDiskUsage());
        assertEquals(4L, dto.getCpuCount());
        assertEquals(8192L, dto.getTotalMemory());
        assertEquals(100000L, dto.getTotalDiskSpace());
        assertEquals(ts, dto.getLastUpdateTime());
        assertEquals(ServiceStatus.ACTIVE, dto.getStatus());
    }

    @Test
    public void testGetOutdatedServiceInfos() throws Exception {
        String serviceId = "tbmq";
        String serviceType = TBMQ.name();

        when(hashOperations.entries(SERVICE_REGISTRY_KEY)).thenReturn(Map.of(serviceId, serviceType));

        SettableFuture<List<TsKvEntry>> future = getNullListSettableFuture(System.currentTimeMillis());
        when(timeseriesService.findLatest(eq(serviceId), anyList())).thenReturn(future);

        var resultFuture = systemInfoService.getServiceInfos();
        PageData<ServiceInfoDto> result = resultFuture.get();

        assertEquals(1, result.getData().size());
        ServiceInfoDto dto = result.getData().get(0);
        assertEquals(serviceId, dto.getServiceId());
        assertEquals(serviceType, dto.getServiceType());
        assertNull(dto.getCpuUsage());
        assertNull(dto.getMemoryUsage());
        assertNull(dto.getDiskUsage());
        assertNull(dto.getCpuCount());
        assertNull(dto.getTotalMemory());
        assertNull(dto.getTotalDiskSpace());
        assertEquals(0, dto.getLastUpdateTime());
        assertEquals(ServiceStatus.OUTDATED, dto.getStatus());
    }

    private SettableFuture<List<TsKvEntry>> getListSettableFuture(long ts) {
        List<TsKvEntry> kvEntries = List.of(
                new BasicTsKvEntry(ts, new LongDataEntry(CPU_USAGE, 10L)),
                new BasicTsKvEntry(ts, new LongDataEntry(MEMORY_USAGE, 1000L)),
                new BasicTsKvEntry(ts, new LongDataEntry(DISK_USAGE, 500L)),
                new BasicTsKvEntry(ts, new LongDataEntry(CPU_COUNT, 4L)),
                new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_MEMORY, 8192L)),
                new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_DISK_SPACE, 100000L))
        );

        SettableFuture<List<TsKvEntry>> future = SettableFuture.create();
        future.set(kvEntries);
        return future;
    }

    private SettableFuture<List<TsKvEntry>> getNullListSettableFuture(long ts) {
        List<TsKvEntry> kvEntries = List.of(
                new BasicTsKvEntry(ts, new LongDataEntry(CPU_USAGE, null)),
                new BasicTsKvEntry(ts, new LongDataEntry(MEMORY_USAGE, null)),
                new BasicTsKvEntry(ts, new LongDataEntry(DISK_USAGE, null)),
                new BasicTsKvEntry(ts, new LongDataEntry(CPU_COUNT, null)),
                new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_MEMORY, null)),
                new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_DISK_SPACE, null))
        );

        SettableFuture<List<TsKvEntry>> future = SettableFuture.create();
        future.set(kvEntries);
        return future;
    }
}
