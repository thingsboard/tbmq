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
package org.thingsboard.mqtt.broker.service.system;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.util.SystemUtil;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.gen.queue.SystemInfoProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.thingsboard.mqtt.broker.cache.CacheConstants.SERVICE_REGISTRY_KEY;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_COUNT;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DISK_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_DISK_SPACE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_MEMORY;
import static org.thingsboard.mqtt.broker.common.data.queue.ServiceType.TBMQ;
import static org.thingsboard.mqtt.broker.common.data.queue.ServiceType.TBMQ_INTEGRATION_EXECUTOR;

public class TbmqSystemInfoServiceTest {

    @Mock
    private ServiceInfoProvider serviceInfoProvider;
    @Mock
    private TimeseriesService timeseriesService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private CacheProperties cacheProperties;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ResourceUsageAlertService resourceUsageAlertService;

    @InjectMocks
    private TbmqSystemInfoService systemInfoService;

    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        systemInfoService = new TbmqSystemInfoService(serviceInfoProvider, timeseriesService, redisTemplate, cacheProperties, resourceUsageAlertService);
        systemInfoService.setServiceRegistryKey(SERVICE_REGISTRY_KEY);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testSaveCurrentServiceInfoKeepsUsageAsHistoryAndCapacityAsLatest() {
        ServiceInfo serviceInfo = ServiceInfo.newBuilder().setServiceId("test-service").setServiceType(TBMQ.name()).build();
        when(serviceInfoProvider.getServiceInfo()).thenReturn(serviceInfo);
        when(serviceInfoProvider.getServiceType()).thenReturn("tbmq");
        when(timeseriesService.save(eq("test-service"), anyList())).thenReturn(Futures.immediateFuture(null));
        when(timeseriesService.saveLatest(eq("test-service"), anyList())).thenReturn(Futures.immediateFuture(null));

        try (MockedStatic<SystemUtil> systemUtil = mockStatic(SystemUtil.class)) {
            systemUtil.when(SystemUtil::getMemoryUsage).thenReturn(Optional.of(20));
            systemUtil.when(SystemUtil::getCpuUsage).thenReturn(Optional.of(10));
            systemUtil.when(SystemUtil::getDiskSpaceUsage).thenReturn(Optional.of(30));
            systemUtil.when(SystemUtil::getTotalMemory).thenReturn(Optional.of(8192L));
            systemUtil.when(SystemUtil::getCpuCount).thenReturn(Optional.of(4));
            systemUtil.when(SystemUtil::getTotalDiskSpace).thenReturn(Optional.of(100000L));

            systemInfoService.saveCurrentServiceInfo();
        }

        verify(resourceUsageAlertService).evaluate("test-service", TBMQ.name(), Map.of(CPU_USAGE, 10L, MEMORY_USAGE, 20L, DISK_USAGE, 30L));
        assertThat(captureKeys(true, "test-service")).containsExactlyInAnyOrder(CPU_USAGE, MEMORY_USAGE, DISK_USAGE);
        assertThat(captureKeys(false, "test-service")).containsExactlyInAnyOrder(TOTAL_MEMORY, CPU_COUNT, TOTAL_DISK_SPACE);
    }

    @Test
    public void testSaveCurrentServiceInfoSavesAndDoesNotThrowWhenAlertEvaluationFails() {
        ServiceInfo serviceInfo = ServiceInfo.newBuilder().setServiceId("test-service").setServiceType(TBMQ.name()).build();
        when(serviceInfoProvider.getServiceInfo()).thenReturn(serviceInfo);
        when(serviceInfoProvider.getServiceType()).thenReturn("tbmq");
        when(timeseriesService.save(eq("test-service"), anyList())).thenReturn(Futures.immediateFuture(null));
        doThrow(new IllegalStateException("settings unavailable")).when(resourceUsageAlertService).evaluate(any(), any(), any());

        try (MockedStatic<SystemUtil> systemUtil = mockStatic(SystemUtil.class)) {
            systemUtil.when(SystemUtil::getMemoryUsage).thenReturn(Optional.of(20));
            systemUtil.when(SystemUtil::getCpuUsage).thenReturn(Optional.of(10));
            systemUtil.when(SystemUtil::getDiskSpaceUsage).thenReturn(Optional.of(30));
            systemUtil.when(SystemUtil::getTotalMemory).thenReturn(Optional.empty());
            systemUtil.when(SystemUtil::getCpuCount).thenReturn(Optional.empty());
            systemUtil.when(SystemUtil::getTotalDiskSpace).thenReturn(Optional.empty());

            assertThatCode(() -> systemInfoService.saveCurrentServiceInfo()).doesNotThrowAnyException();
        }

        assertThat(captureKeys(true, "test-service")).containsExactlyInAnyOrder(CPU_USAGE, MEMORY_USAGE, DISK_USAGE);
    }

    @Test
    public void testSaveCurrentServiceInfoWithoutMetricsSavesNothing() {
        when(serviceInfoProvider.getServiceInfo()).thenReturn(ServiceInfo.newBuilder().setServiceId("test-service").build());

        try (MockedStatic<SystemUtil> systemUtil = mockStatic(SystemUtil.class)) {
            systemUtil.when(SystemUtil::getMemoryUsage).thenReturn(Optional.empty());
            systemUtil.when(SystemUtil::getCpuUsage).thenReturn(Optional.empty());
            systemUtil.when(SystemUtil::getDiskSpaceUsage).thenReturn(Optional.empty());
            systemUtil.when(SystemUtil::getTotalMemory).thenReturn(Optional.empty());
            systemUtil.when(SystemUtil::getCpuCount).thenReturn(Optional.empty());
            systemUtil.when(SystemUtil::getTotalDiskSpace).thenReturn(Optional.empty());

            systemInfoService.saveCurrentServiceInfo();
        }

        verifyNoInteractions(resourceUsageAlertService, timeseriesService);
    }

    @Test
    public void testProcessIeServiceInfoWithoutSystemInfo() {
        ServiceInfo serviceInfo = ServiceInfo
                .newBuilder()
                .setServiceId("svc1")
                .setServiceType("type1")
                .build();

        systemInfoService.processIeServiceInfo(serviceInfo);

        verify(hashOperations).put(SERVICE_REGISTRY_KEY, "type1:svc1", "type1");
    }

    @Test
    public void testProcessIeServiceInfoWithSystemInfoStoresUnderTypedEntityId() {
        String entityId = "TBMQ_INTEGRATION_EXECUTOR:svc2";
        when(timeseriesService.save(eq(entityId), anyList())).thenReturn(Futures.immediateFuture(null));
        when(timeseriesService.saveLatest(eq(entityId), anyList())).thenReturn(Futures.immediateFuture(null));

        ServiceInfo serviceInfo = ServiceInfo
                .newBuilder()
                .setServiceId("svc2")
                .setServiceType(TBMQ_INTEGRATION_EXECUTOR.name())
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

        verify(resourceUsageAlertService).evaluate("svc2", TBMQ_INTEGRATION_EXECUTOR.name(),
                Map.of(CPU_USAGE, 10L, MEMORY_USAGE, 200L, DISK_USAGE, 50L));
        assertThat(captureKeys(true, entityId)).containsExactlyInAnyOrder(CPU_USAGE, MEMORY_USAGE, DISK_USAGE);
        assertThat(captureKeys(false, entityId)).containsExactlyInAnyOrder(TOTAL_MEMORY, CPU_COUNT, TOTAL_DISK_SPACE);
        verify(timeseriesService, never()).save(eq("svc2"), anyList());
    }

    @Test
    public void testProcessIeServiceInfoWithEmptySystemInfoSavesNothing() {
        ServiceInfo serviceInfo = ServiceInfo.newBuilder()
                .setServiceId("svc2")
                .setServiceType(TBMQ_INTEGRATION_EXECUTOR.name())
                .setSystemInfo(SystemInfoProto.getDefaultInstance())
                .build();

        systemInfoService.processIeServiceInfo(serviceInfo);

        verifyNoInteractions(resourceUsageAlertService, timeseriesService);
    }

    @Test
    public void testProcessIeServiceInfoWithOnlyCapacitySkipsHistory() {
        String entityId = "TBMQ_INTEGRATION_EXECUTOR:svc2";
        when(timeseriesService.saveLatest(eq(entityId), anyList())).thenReturn(Futures.immediateFuture(null));
        ServiceInfo serviceInfo = ServiceInfo.newBuilder()
                .setServiceId("svc2")
                .setServiceType(TBMQ_INTEGRATION_EXECUTOR.name())
                .setSystemInfo(SystemInfoProto.newBuilder().setCpuCount(4).build())
                .build();

        systemInfoService.processIeServiceInfo(serviceInfo);

        verify(resourceUsageAlertService).evaluate("svc2", TBMQ_INTEGRATION_EXECUTOR.name(), Map.of());
        verify(timeseriesService, never()).save(any(), anyList());
        assertThat(captureKeys(false, entityId)).containsExactly(CPU_COUNT);
    }

    @Test
    public void testRemoveServiceInfoExists() {
        when(hashOperations.delete(SERVICE_REGISTRY_KEY, "svc3")).thenReturn(1L);
        assertDoesNotThrow(() -> systemInfoService.removeServiceInfo("svc3", TBMQ));
    }

    @Test
    public void testRemoveServiceInfoNotFound() {
        when(hashOperations.delete(eq(SERVICE_REGISTRY_KEY), any(Object[].class))).thenReturn(0L);
        assertThrows(ThingsboardException.class, () -> systemInfoService.removeServiceInfo("svc4", null));
    }

    @Test
    public void testRemoveExecutorServiceInfoDeletesTypedAndLegacyFields() {
        when(hashOperations.get(SERVICE_REGISTRY_KEY, "ie1")).thenReturn(TBMQ_INTEGRATION_EXECUTOR.name());
        when(hashOperations.delete(SERVICE_REGISTRY_KEY, "TBMQ_INTEGRATION_EXECUTOR:ie1", "ie1")).thenReturn(2L);

        assertDoesNotThrow(() -> systemInfoService.removeServiceInfo("ie1", TBMQ_INTEGRATION_EXECUTOR));

        verify(hashOperations).delete(SERVICE_REGISTRY_KEY, "TBMQ_INTEGRATION_EXECUTOR:ie1", "ie1");
    }

    @Test
    public void testRemoveExecutorServiceInfoKeepsBrokerSharingTheId() {
        when(hashOperations.get(SERVICE_REGISTRY_KEY, "shared")).thenReturn(TBMQ.name());
        when(hashOperations.delete(SERVICE_REGISTRY_KEY, "TBMQ_INTEGRATION_EXECUTOR:shared")).thenReturn(1L);

        assertDoesNotThrow(() -> systemInfoService.removeServiceInfo("shared", TBMQ_INTEGRATION_EXECUTOR));

        verify(hashOperations).delete(SERVICE_REGISTRY_KEY, "TBMQ_INTEGRATION_EXECUTOR:shared");
        verify(hashOperations, never()).delete(SERVICE_REGISTRY_KEY, "TBMQ_INTEGRATION_EXECUTOR:shared", "shared");
        verify(hashOperations, never()).delete(SERVICE_REGISTRY_KEY, "shared");
    }

    @Test
    public void testRemoveServiceInfoWithoutTypeDeletesEveryVariant() {
        when(hashOperations.delete(eq(SERVICE_REGISTRY_KEY), any(Object[].class))).thenReturn(2L);
        assertDoesNotThrow(() -> systemInfoService.removeServiceInfo("shared", null));
        verify(hashOperations).delete(SERVICE_REGISTRY_KEY, "shared", "TBMQ_INTEGRATION_EXECUTOR:shared");
    }

    @Test
    public void testRegistryFieldKeepsBrokersBareAndPrefixesExecutors() {
        assertEquals("node1", ServiceInfoIds.registryField("node1", TBMQ.name()));
        assertEquals("TBMQ_INTEGRATION_EXECUTOR:node1", ServiceInfoIds.registryField("node1", TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    public void testTimeseriesEntityIdFollowsRegistryField() {
        assertEquals("node1", ServiceInfoIds.timeseriesEntityId("node1", TBMQ.name()));
        assertEquals("TBMQ_INTEGRATION_EXECUTOR:node1", ServiceInfoIds.timeseriesEntityId("node1", TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    public void testAuditEntityIdIsStablePerTypeAndId() {
        assertEquals(ServiceInfoIds.auditEntityId("node1", TBMQ.name()), ServiceInfoIds.auditEntityId("node1", TBMQ.name()));
        assertNotEquals(ServiceInfoIds.auditEntityId("node1", TBMQ.name()), ServiceInfoIds.auditEntityId("node1", TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    public void testParseRegistryEntryUnderstandsLegacyAndTypedFields() {
        assertEquals(new TbmqSystemInfoService.RegistryEntry("node1", TBMQ.name()),
                TbmqSystemInfoService.parseRegistryEntry("node1", TBMQ.name()));
        assertEquals(new TbmqSystemInfoService.RegistryEntry("node1", TBMQ_INTEGRATION_EXECUTOR.name()),
                TbmqSystemInfoService.parseRegistryEntry("TBMQ_INTEGRATION_EXECUTOR:node1", TBMQ_INTEGRATION_EXECUTOR.name()));
        assertEquals(new TbmqSystemInfoService.RegistryEntry("host:1883", TBMQ.name()),
                TbmqSystemInfoService.parseRegistryEntry("host:1883", TBMQ.name()));
    }

    @Test
    public void testGetServiceInfosListsBrokerAndExecutorSharingAnId() throws Exception {
        when(hashOperations.entries(SERVICE_REGISTRY_KEY)).thenReturn(
                Map.of("shared", TBMQ.name(), "TBMQ_INTEGRATION_EXECUTOR:shared", TBMQ_INTEGRATION_EXECUTOR.name()));
        long ts = System.currentTimeMillis();
        when(timeseriesService.findLatest(eq("shared"), anyList())).thenReturn(Futures.immediateFuture(
                List.of(new BasicTsKvEntry(ts, new LongDataEntry(CPU_USAGE, 11L)))));
        when(timeseriesService.findLatest(eq("TBMQ_INTEGRATION_EXECUTOR:shared"), anyList())).thenReturn(Futures.immediateFuture(
                List.of(new BasicTsKvEntry(ts, new LongDataEntry(CPU_USAGE, 77L)))));

        PageData<ServiceInfoDto> result = systemInfoService.getServiceInfos().get();

        assertEquals(2, result.getData().size());
        assertThat(result.getData()).extracting(ServiceInfoDto::getServiceId).containsOnly("shared");
        assertThat(result.getData()).extracting(ServiceInfoDto::getServiceType, ServiceInfoDto::getCpuUsage)
                .containsExactlyInAnyOrder(tuple(TBMQ.name(), 11L), tuple(TBMQ_INTEGRATION_EXECUTOR.name(), 77L));
    }

    @Test
    public void testProcessIeServiceInfoRegistersUnderTypedFieldAndDropsLegacyOne() {
        ServiceInfo serviceInfo = ServiceInfo.newBuilder().setServiceId("ie1").setServiceType(TBMQ_INTEGRATION_EXECUTOR.name()).build();
        when(hashOperations.get(SERVICE_REGISTRY_KEY, "ie1")).thenReturn(TBMQ_INTEGRATION_EXECUTOR.name());

        systemInfoService.processIeServiceInfo(serviceInfo);

        verify(hashOperations).put(SERVICE_REGISTRY_KEY, "TBMQ_INTEGRATION_EXECUTOR:ie1", TBMQ_INTEGRATION_EXECUTOR.name());
        verify(hashOperations).delete(SERVICE_REGISTRY_KEY, "ie1");
    }

    @Test
    public void testProcessIeServiceInfoKeepsBareFieldOfBrokerSharingTheId() {
        ServiceInfo serviceInfo = ServiceInfo.newBuilder().setServiceId("shared").setServiceType(TBMQ_INTEGRATION_EXECUTOR.name()).build();
        when(hashOperations.get(SERVICE_REGISTRY_KEY, "shared")).thenReturn(TBMQ.name());

        systemInfoService.processIeServiceInfo(serviceInfo);

        verify(hashOperations).put(SERVICE_REGISTRY_KEY, "TBMQ_INTEGRATION_EXECUTOR:shared", TBMQ_INTEGRATION_EXECUTOR.name());
        verify(hashOperations, never()).delete(SERVICE_REGISTRY_KEY, "shared");
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

    @Test
    public void testGetTbmqServiceIds() {
        when(hashOperations.entries(SERVICE_REGISTRY_KEY)).thenReturn(
                Map.of("tbmq1", TBMQ.name(), "tbmq-ie1", TBMQ_INTEGRATION_EXECUTOR.name(),
                        "tbmq2", TBMQ.name(), "TBMQ_INTEGRATION_EXECUTOR:tbmq2", TBMQ_INTEGRATION_EXECUTOR.name())
        );

        List<String> tbmqServiceIds = systemInfoService.getTbmqServiceIds();
        assertEquals(2, tbmqServiceIds.size());
        assertThat(tbmqServiceIds).containsAll(List.of("tbmq1", "tbmq2"));
    }

    @SuppressWarnings("unchecked")
    private List<String> captureKeys(boolean history, String entityId) {
        ArgumentCaptor<List<TsKvEntry>> captor = ArgumentCaptor.forClass(List.class);
        if (history) {
            verify(timeseriesService).save(eq(entityId), captor.capture());
        } else {
            verify(timeseriesService).saveLatest(eq(entityId), captor.capture());
        }
        return captor.getValue().stream().map(TsKvEntry::getKey).toList();
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
