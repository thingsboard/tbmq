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
package org.thingsboard.mqtt.broker.integration.service.system;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.ServiceInfo;
import org.thingsboard.mqtt.broker.gen.queue.SystemInfoProto;
import org.thingsboard.mqtt.broker.integration.service.api.IntegrationApiService;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.SystemInfoService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getCpuCount;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getCpuUsage;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getDiscSpaceUsage;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getMemoryUsage;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getTotalDiscSpace;
import static org.thingsboard.mqtt.broker.common.util.SystemUtil.getTotalMemory;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationExecutorSystemInfoService implements SystemInfoService {

    private final ServiceInfoProvider serviceInfoProvider;
    private final IntegrationApiService apiService;

    private ScheduledExecutorService scheduler;

    @Value("${stats.system-info.persist-frequency:60}")
    private int systemInfoPersistFrequencySec;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("ie-system-info-scheduler"));
        scheduler.scheduleAtFixedRate(this::sendCurrentServiceInfo, systemInfoPersistFrequencySec, systemInfoPersistFrequencySec, TimeUnit.SECONDS);
        // sending service info without system info to update service registry
        apiService.sendServiceInfo(serviceInfoProvider.getServiceInfo());
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(scheduler, "IE system info scheduler");
        }
    }

    @Override
    public void sendCurrentServiceInfo() {
        ServiceInfo serviceInfo = serviceInfoProvider
                .getServiceInfo()
                .toBuilder()
                .setSystemInfo(buildSystemInfoProto())
                .build();
        apiService.sendServiceInfo(serviceInfo);
    }

    private SystemInfoProto buildSystemInfoProto() {
        SystemInfoProto.Builder builder = SystemInfoProto.newBuilder();

        getMemoryUsage().ifPresent(builder::setMemoryUsage);
        getCpuUsage().ifPresent(builder::setCpuUsage);
        getDiscSpaceUsage().ifPresent(builder::setDiskUsage);

        getTotalMemory().ifPresent(builder::setTotalMemory);
        getCpuCount().ifPresent(builder::setCpuCount);
        getTotalDiscSpace().ifPresent(builder::setTotalDiscSpace);

        return builder.build();
    }

}
