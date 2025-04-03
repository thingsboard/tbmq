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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.gen.queue.SystemInfoProto;
import org.thingsboard.mqtt.broker.integration.api.data.ServiceInfoDto;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.SystemInfoService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.cache.CacheConstants.SERVICE_REGISTRY_KEY;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_COUNT;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.DISK_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.SERVICE_INFO_KEYS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_DISK_SPACE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.TOTAL_MEMORY;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.UNKNOWN;
import static org.thingsboard.mqtt.broker.common.util.DonAsynchron.withCallback;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getCpuCount;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getCpuUsage;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getDiscSpaceUsage;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getMemoryUsage;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getTotalDiscSpace;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getTotalMemory;

@Service
@RequiredArgsConstructor
@Slf4j
public class TbmqSystemInfoService implements SystemInfoService {

    private static final int KEYS_TO_UPDATE = 6;

    private final ServiceInfoProvider serviceInfoProvider;
    private final TimeseriesService timeseriesService;
    private final RedisTemplate<String, Object> redisTemplate;

    private ScheduledExecutorService scheduler;

    @Value("${stats.system-info.persist-frequency:60}")
    private int systemInfoPersistFrequencySec;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("tbmq-system-info-scheduler"));
        scheduler.scheduleAtFixedRate(this::sendCurrentServiceInfo, systemInfoPersistFrequencySec, systemInfoPersistFrequencySec, TimeUnit.SECONDS);

        updateServiceRegistry(serviceInfoProvider.getServiceInfo());
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(scheduler, "TBMQ system info scheduler");
        }
    }

    @Override
    public void sendCurrentServiceInfo() {
        ServiceInfo serviceInfo = serviceInfoProvider.getServiceInfo();

        long ts = System.currentTimeMillis();
        List<TsKvEntry> tsList = new ArrayList<>(KEYS_TO_UPDATE);

        getMemoryUsage().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(MEMORY_USAGE, (long) value))));
        getCpuUsage().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(CPU_USAGE, (long) value))));
        getDiscSpaceUsage().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(DISK_USAGE, (long) value))));
        getTotalMemory().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_MEMORY, value))));
        getCpuCount().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(CPU_COUNT, (long) value))));
        getTotalDiscSpace().ifPresent(value -> tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_DISK_SPACE, value))));

        withCallback(timeseriesService.saveLatest(serviceInfo.getServiceId(), tsList), __ -> {
        }, throwable -> log.warn("Failed to process TBMQ service info {}", serviceInfo, throwable));
    }

    @Override
    public void processServiceInfo(ServiceInfo serviceInfo) {
        log.trace("processServiceInfo: [{}]", serviceInfo);

        if (!serviceInfo.hasSystemInfo()) {
            updateServiceRegistry(serviceInfo);
        }

        SystemInfoProto systemInfo = serviceInfo.getSystemInfo();
        long ts = System.currentTimeMillis();
        List<TsKvEntry> tsList = new ArrayList<>(KEYS_TO_UPDATE);
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
        if (systemInfo.hasTotalDiscSpace()) {
            tsList.add(new BasicTsKvEntry(ts, new LongDataEntry(TOTAL_DISK_SPACE, systemInfo.getTotalDiscSpace())));
        }

        withCallback(timeseriesService.saveLatest(serviceInfo.getServiceId(), tsList), __ -> {
        }, throwable -> log.warn("Failed to process IE service info {}", serviceInfo, throwable));
    }

    @Override
    public ListenableFuture<List<ServiceInfoDto>> getServiceInfos() throws ThingsboardException {
        Map<Object, Object> serviceMap = redisTemplate.opsForHash().entries(SERVICE_REGISTRY_KEY);
        if (CollectionUtils.isEmpty(serviceMap)) {
            throw new ThingsboardException("Could not find all service ids", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }

        List<ListenableFuture<List<TsKvEntry>>> futures = new ArrayList<>(serviceMap.size());
        List<String> serviceIds = new ArrayList<>(serviceMap.size());

        for (Map.Entry<Object, Object> entry : serviceMap.entrySet()) {
            String serviceId = String.valueOf(entry.getKey());
            serviceIds.add(serviceId);
            futures.add(timeseriesService.findLatest(serviceId, SERVICE_INFO_KEYS));
        }

        return Futures.transform(Futures.allAsList(futures), allMetrics -> {
            List<ServiceInfoDto> response = new ArrayList<>(serviceMap.size());

            for (int i = 0; i < serviceIds.size(); i++) {
                String serviceId = serviceIds.get(i);
                List<TsKvEntry> entries = allMetrics.get(i);

                ServiceInfoDto dto = new ServiceInfoDto();
                dto.setServiceId(serviceId);
                dto.setServiceType(Optional.ofNullable(serviceMap.get(serviceId)).map(Object::toString).orElse(UNKNOWN));
                for (TsKvEntry entry : entries) {
                    switch (entry.getKey()) {
                        case CPU_USAGE -> dto.setCpuUsage(entry.getLongValue().orElse(null));
                        case CPU_COUNT -> dto.setCpuCount(entry.getLongValue().orElse(null));
                        case MEMORY_USAGE -> dto.setMemoryUsage(entry.getLongValue().orElse(null));
                        case TOTAL_MEMORY -> dto.setTotalMemory(entry.getLongValue().orElse(null));
                        case DISK_USAGE -> dto.setDiscUsage(entry.getLongValue().orElse(null));
                        case TOTAL_DISK_SPACE -> dto.setTotalDiscSpace(entry.getLongValue().orElse(null));
                    }
                    dto.setLastUpdateTime(entry.getTs());
                }
                response.add(dto);
            }
            return response;
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void removeServiceInfo(String serviceId) throws ThingsboardException {
        Long delete = redisTemplate.opsForHash().delete(SERVICE_REGISTRY_KEY, serviceId);
        if (delete == 0) {
            throw new ThingsboardException("Provided service id is not found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
    }

    private void updateServiceRegistry(ServiceInfo serviceInfo) {
        redisTemplate.opsForHash().put(SERVICE_REGISTRY_KEY, serviceInfo.getServiceId(), serviceInfo.getServiceType());
    }
}
