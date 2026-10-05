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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.cache.CacheConstants.SERVICE_REGISTRY_KEY;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_COUNT;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DISK_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SERVICE_INFO_KEYS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SERVICE_INFO_KEYS_COUNT;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_DISK_SPACE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_MEMORY;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.UNKNOWN;
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
    private static final String REGISTRY_FIELD_SEPARATOR = ":";

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

        if (tsList.isEmpty()) {
            return;
        }
        resourceUsageAlertService.evaluate(serviceInfo.getServiceId(), serviceInfoProvider.getServiceType(), usagePercents(tsList));
        // Stored as history (not latest-only) so the UI can chart CPU, memory and disk per node.
        withCallback(timeseriesService.save(serviceInfo.getServiceId(), tsList), __ -> {
        }, throwable -> log.warn("Failed to process TBMQ service info {}", serviceInfo, throwable));
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

        if (tsList.isEmpty()) {
            return;
        }
        resourceUsageAlertService.evaluate(serviceInfo.getServiceId(), serviceInfo.getServiceType(), usagePercents(tsList));
        withCallback(timeseriesService.save(serviceInfo.getServiceId(), tsList), __ -> {
        }, throwable -> log.warn("Failed to process IE service info {}", serviceInfo, throwable));
    }

    private static Map<String, Long> usagePercents(List<TsKvEntry> tsList) {
        Map<String, Long> usage = new HashMap<>();
        for (TsKvEntry entry : tsList) {
            if (USAGE_PERCENT_KEYS.contains(entry.getKey())) {
                entry.getLongValue().ifPresent(value -> usage.put(entry.getKey(), value));
            }
        }
        return usage;
    }

    @Override
    public ListenableFuture<PageData<ServiceInfoDto>> getServiceInfos() throws ThingsboardException {
        List<RegistryEntry> services = readServiceRegistry();
        if (services.isEmpty()) {
            throw new ThingsboardException("Could not find all service ids", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }

        List<ListenableFuture<List<TsKvEntry>>> futures = new ArrayList<>(services.size());
        for (RegistryEntry service : services) {
            futures.add(timeseriesService.findLatest(service.serviceId(), SERVICE_INFO_KEYS));
        }

        return Futures.transform(Futures.allAsList(futures), allMetrics -> {
            List<ServiceInfoDto> response = new ArrayList<>(services.size());

            for (int i = 0; i < services.size(); i++) {
                RegistryEntry service = services.get(i);
                List<TsKvEntry> entries = allMetrics.get(i);

                ServiceInfoDto dto = new ServiceInfoDto();
                dto.setServiceId(service.serviceId());
                dto.setServiceType(service.serviceType());
                for (TsKvEntry entry : entries) {
                    switch (entry.getKey()) {
                        case CPU_USAGE -> dto.setCpuUsage(entry.getLongValue().orElse(null));
                        case CPU_COUNT -> dto.setCpuCount(entry.getLongValue().orElse(null));
                        case MEMORY_USAGE -> dto.setMemoryUsage(entry.getLongValue().orElse(null));
                        case TOTAL_MEMORY -> dto.setTotalMemory(entry.getLongValue().orElse(null));
                        case DISK_USAGE -> dto.setDiskUsage(entry.getLongValue().orElse(null));
                        case TOTAL_DISK_SPACE -> dto.setTotalDiskSpace(entry.getLongValue().orElse(null));
                    }
                    dto.setLastUpdateTime(dto.isDataPresent() ? entry.getTs() : 0L);
                }
                dto.setStatus(ServiceStatus.fromLastUpdateTime(dto.getLastUpdateTime()));
                response.add(dto);
            }
            Collections.sort(response);
            return new PageData<>(response, 1, response.size(), false);
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void removeServiceInfo(String serviceId, String serviceType) throws ThingsboardException {
        Object[] fields = serviceType == null
                ? Stream.concat(Stream.of(serviceId), Arrays.stream(ServiceType.values()).map(type -> registryField(serviceId, type.name()))).distinct().toArray()
                : Stream.of(serviceId, registryField(serviceId, serviceType)).distinct().toArray();
        Long delete = redisTemplate.opsForHash().delete(serviceRegistryKey, fields);
        if (delete == 0) {
            throw new ThingsboardException("Provided service id is not found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
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
        String field = registryField(serviceInfo.getServiceId(), serviceInfo.getServiceType());
        redisTemplate.opsForHash().put(serviceRegistryKey, field, serviceInfo.getServiceType());
        if (!field.equals(serviceInfo.getServiceId())) {
            // Drop the pre-upgrade entry of this executor, which was keyed by the bare service id.
            Object legacyType = redisTemplate.opsForHash().get(serviceRegistryKey, serviceInfo.getServiceId());
            if (serviceInfo.getServiceType().equals(legacyType)) {
                redisTemplate.opsForHash().delete(serviceRegistryKey, serviceInfo.getServiceId());
            }
        }
    }

    /**
     * Brokers are registered under their bare service id, as they always were, so older nodes keep finding them.
     * Every other service type is registered as {@code <type>:<serviceId>}, so an executor and a broker that share
     * a service id no longer overwrite each other. Entries written before this scheme are still understood.
     */
    static String registryField(String serviceId, String serviceType) {
        return ServiceType.TBMQ.name().equals(serviceType) ? serviceId : serviceType + REGISTRY_FIELD_SEPARATOR + serviceId;
    }

    static RegistryEntry parseRegistryEntry(String field, String value) {
        int separator = field.indexOf(REGISTRY_FIELD_SEPARATOR);
        if (separator > 0) {
            String prefix = field.substring(0, separator);
            if (Arrays.stream(ServiceType.values()).anyMatch(type -> type.name().equals(prefix))) {
                return new RegistryEntry(field.substring(separator + 1), prefix);
            }
        }
        return new RegistryEntry(field, value == null ? UNKNOWN : value);
    }

    private List<RegistryEntry> readServiceRegistry() {
        Map<Object, Object> serviceMap = redisTemplate.opsForHash().entries(serviceRegistryKey);
        if (CollectionUtils.isEmpty(serviceMap)) {
            return List.of();
        }
        return serviceMap.entrySet().stream()
                .map(entry -> parseRegistryEntry(String.valueOf(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue())))
                .distinct()
                .toList();
    }

    record RegistryEntry(String serviceId, String serviceType) {
    }
}
