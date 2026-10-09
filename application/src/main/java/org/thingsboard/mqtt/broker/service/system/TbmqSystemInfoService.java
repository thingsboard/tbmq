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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.cache.CacheProperties;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.queue.ServiceType;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.gen.queue.SystemInfoProto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.system.ServiceInfoIds.RegistryEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.thingsboard.mqtt.broker.cache.CacheConstants.SERVICE_REGISTRY_KEY;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_COUNT;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DISK_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SERVICE_INFO_KEYS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SERVICE_INFO_KEYS_COUNT;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_DISK_SPACE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_MEMORY;
import static org.thingsboard.mqtt.broker.common.util.DonAsynchron.withCallback;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getCpuCount;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getCpuUsage;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getDiskSpaceUsage;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getMemoryUsage;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getTotalDiskSpace;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getTotalMemory;

@Service
@RequiredArgsConstructor
@Slf4j
public class TbmqSystemInfoService implements SystemInfoService {

    private final ServiceInfoProvider serviceInfoProvider;
    private final TimeseriesService timeseriesService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;
    private final ResourceUsageAlertService resourceUsageAlertService;

    private static final Set<String> USAGE_PERCENT_KEYS = Set.of(CPU_USAGE, MEMORY_USAGE, DISK_USAGE);

    private ScheduledExecutorService scheduler;
    @Setter
    private String serviceRegistryKey;

    @Value("${stats.system-info.persist-frequency:60}")
    private int systemInfoPersistFrequencySec;

    @PostConstruct
    public void init() {
        serviceRegistryKey = cacheProperties.prefixKey(SERVICE_REGISTRY_KEY);
        scheduler = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("tbmq-system-info-scheduler"));
        scheduler.scheduleAtFixedRate(this::saveCurrentServiceInfo, systemInfoPersistFrequencySec, systemInfoPersistFrequencySec, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> updateServiceRegistry(serviceInfoProvider.getServiceInfo()), 0, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(scheduler, "TBMQ system info scheduler");
        }
    }

    @Override
    public void saveCurrentServiceInfo() {
        log.trace("Executing saveCurrentServiceInfo");
        ServiceInfo serviceInfo = serviceInfoProvider.getServiceInfo();

        long ts = System.currentTimeMillis();
        List<TsKvEntry> tsList = new ArrayList<>(SERVICE_INFO_KEYS_COUNT);

        getMemoryUsage().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(MEMORY_USAGE, (long) value))));
        getCpuUsage().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(CPU_USAGE, (long) value))));
        getDiskSpaceUsage().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(DISK_USAGE, (long) value))));
        getTotalMemory().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_MEMORY, value))));
        getCpuCount().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(CPU_COUNT, (long) value))));
        getTotalDiskSpace().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_DISK_SPACE, value))));

        processServiceInfoSample(serviceInfo.getServiceId(), serviceInfo.getServiceType(), tsList);
    }

    @Override
    public void processIeServiceInfo(ServiceInfo serviceInfo) {
        log.trace("Executing processServiceInfo: [{}]", serviceInfo);

        if (!serviceInfo.hasSystemInfo()) {
            updateServiceRegistry(serviceInfo);
            return;
        }

        SystemInfoProto systemInfo = serviceInfo.getSystemInfo();
        long ts = System.currentTimeMillis();
        List<TsKvEntry> tsList = new ArrayList<>(SERVICE_INFO_KEYS_COUNT);
        if (systemInfo.hasCpuUsage()) {
            tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(CPU_USAGE, systemInfo.getCpuUsage())));
        }
        if (systemInfo.hasMemoryUsage()) {
            tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(MEMORY_USAGE, systemInfo.getMemoryUsage())));
        }
        if (systemInfo.hasDiskUsage()) {
            tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(DISK_USAGE, systemInfo.getDiskUsage())));
        }
        if (systemInfo.hasCpuCount()) {
            tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(CPU_COUNT, systemInfo.getCpuCount())));
        }
        if (systemInfo.hasTotalMemory()) {
            tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_MEMORY, systemInfo.getTotalMemory())));
        }
        if (systemInfo.hasTotalDiskSpace()) {
            tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_DISK_SPACE, systemInfo.getTotalDiskSpace())));
        }

        processServiceInfoSample(serviceInfo.getServiceId(), serviceInfo.getServiceType(), tsList);
    }

    /**
     * Stores one sample of a node's system info and checks its usage percents against the alert thresholds.
     */
    private void processServiceInfoSample(String serviceId, String serviceType, List<TsKvEntry> tsList) {
        if (tsList.isEmpty()) {
            return;
        }
        String entityId = ServiceInfoIds.timeseriesEntityId(serviceId, serviceType);
        // Only the usage percents are charted, so only they are kept as history; capacity values stay latest-only.
        Map<Boolean, List<TsKvEntry>> entriesByHistory = tsList.stream()
                .collect(Collectors.partitioningBy(entry -> USAGE_PERCENT_KEYS.contains(entry.getKey())));
        List<TsKvEntry> usageEntries = entriesByHistory.get(true);
        List<TsKvEntry> capacityEntries = entriesByHistory.get(false);
        if (!capacityEntries.isEmpty()) {
            withCallback(timeseriesService.saveLatest(entityId, capacityEntries), __ -> {
            }, throwable -> log.warn("[{}] Failed to save service info {}", entityId, capacityEntries, throwable));
        }
        if (!usageEntries.isEmpty()) {
            withCallback(timeseriesService.save(entityId, usageEntries), __ -> {
            }, throwable -> log.warn("[{}] Failed to save service usage {}", entityId, usageEntries, throwable));
            evaluateAlerts(serviceId, serviceType, usageEntries);
        }
    }

    private void evaluateAlerts(String serviceId, String serviceType, List<TsKvEntry> usageEntries) {
        Map<String, Long> usagePercents = new HashMap<>();
        usageEntries.forEach(entry -> entry.getLongValue().ifPresent(value -> usagePercents.put(entry.getKey(), value)));
        ServiceType type = ServiceType.findByName(serviceType).orElse(null);
        if (type == null) {
            log.debug("[{}] Skipping resource usage alerts of unknown service type {}", serviceId, serviceType);
            return;
        }
        // Runs on the persist scheduler: an exception escaping here would cancel every later run of scheduleAtFixedRate.
        try {
            resourceUsageAlertService.evaluate(serviceId, type, usagePercents);
        } catch (Exception e) {
            log.warn("[{}] Failed to evaluate resource usage alerts", serviceId, e);
        }
    }

    @Override
    public ListenableFuture<PageData<ServiceInfoDto>> getServiceInfos() throws ThingsboardException {
        List<RegistryEntry> services = readServiceRegistry();
        if (services.isEmpty()) {
            throw new ThingsboardException("Could not find all service ids", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }

        List<String> entityIds = services.stream()
                .map(service -> ServiceInfoIds.timeseriesEntityId(service.serviceId(), service.serviceType()))
                .toList();

        return Futures.transform(timeseriesService.findLatestByEntityIds(entityIds, SERVICE_INFO_KEYS), latestByEntityId -> {
            List<ServiceInfoDto> response = new ArrayList<>(services.size());

            for (int i = 0; i < services.size(); i++) {
                RegistryEntry service = services.get(i);
                List<TsKvEntry> entries = latestByEntityId.getOrDefault(entityIds.get(i), List.of());

                ServiceInfoDto dto = new ServiceInfoDto();
                dto.setServiceId(service.serviceId());
                dto.setServiceType(service.serviceType());
                dto.setLastUpdateTime(0L);
                for (TsKvEntry entry : entries) {
                    switch (entry.getKey()) {
                        case CPU_USAGE -> dto.setCpuUsage(entry.getLongValue().orElse(null));
                        case CPU_COUNT -> dto.setCpuCount(entry.getLongValue().orElse(null));
                        case MEMORY_USAGE -> dto.setMemoryUsage(entry.getLongValue().orElse(null));
                        case TOTAL_MEMORY -> dto.setTotalMemory(entry.getLongValue().orElse(null));
                        case DISK_USAGE -> dto.setDiskUsage(entry.getLongValue().orElse(null));
                        case TOTAL_DISK_SPACE -> dto.setTotalDiskSpace(entry.getLongValue().orElse(null));
                    }
                    if (entry.getLongValue().isPresent()) {
                        dto.setLastUpdateTime(Math.max(dto.getLastUpdateTime(), entry.getTs()));
                    }
                }
                dto.setStatus(ServiceStatus.fromLastUpdateTime(dto.getLastUpdateTime()));
                response.add(dto);
            }
            Collections.sort(response);
            return new PageData<>(response, 1, response.size(), false);
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void removeServiceInfo(String serviceId, ServiceType serviceType) throws ThingsboardException {
        Long delete = redisTemplate.opsForHash().delete(serviceRegistryKey, registryFieldsFor(serviceId, serviceType));
        if (delete == 0) {
            throw new ThingsboardException("Provided service id is not found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        List<ServiceType> removedTypes = serviceType == null ? List.of(ServiceType.values()) : List.of(serviceType);
        removedTypes.forEach(type -> resourceUsageAlertService.onServiceRemoved(serviceId, type));
    }

    private Object[] registryFieldsFor(String serviceId, ServiceType serviceType) {
        if (serviceType == null) {
            Stream<String> typedFields = Arrays.stream(ServiceType.values()).map(type -> ServiceInfoIds.registryField(serviceId, type.name()));
            return Stream.concat(Stream.of(serviceId), typedFields)
                    .distinct()
                    .toArray();
        }
        String field = ServiceInfoIds.registryField(serviceId, serviceType.name());
        if (hasLegacyField(serviceId, serviceType.name())) {
            return new Object[]{field, serviceId};
        }
        return new Object[]{field};
    }

    @Override
    public List<String> getTbmqServiceIds() {
        return readServiceRegistry().stream()
                .filter(entry -> ServiceType.TBMQ.name().equals(entry.serviceType()))
                .map(RegistryEntry::serviceId)
                .toList();
    }

    private void updateServiceRegistry(ServiceInfo serviceInfo) {
        log.debug("Updating service registry: {}", serviceInfo);
        String field = ServiceInfoIds.registryField(serviceInfo.getServiceId(), serviceInfo.getServiceType());
        redisTemplate.opsForHash().put(serviceRegistryKey, field, serviceInfo.getServiceType());
        if (hasLegacyField(serviceInfo.getServiceId(), serviceInfo.getServiceType())) {
            redisTemplate.opsForHash().delete(serviceRegistryKey, serviceInfo.getServiceId());
        }
    }

    /**
     * Whether a non-broker service still has its pre-upgrade entry under the bare {@code serviceId} field. A bare
     * field holding another type (a broker that shares the id) must be kept.
     * TODO: remove after 2.5, once every executor has re-registered under its typed field.
     */
    private boolean hasLegacyField(String serviceId, String serviceType) {
        return !ServiceType.TBMQ.name().equals(serviceType)
                && serviceType.equals(redisTemplate.opsForHash().get(serviceRegistryKey, serviceId));
    }

    private List<RegistryEntry> readServiceRegistry() {
        Map<Object, Object> serviceMap = redisTemplate.opsForHash().entries(serviceRegistryKey);
        if (CollectionUtils.isEmpty(serviceMap)) {
            return List.of();
        }
        return serviceMap.entrySet().stream()
                .map(entry -> ServiceInfoIds.parseRegistryField(String.valueOf(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue())))
                .distinct()
                .toList();
    }
}
