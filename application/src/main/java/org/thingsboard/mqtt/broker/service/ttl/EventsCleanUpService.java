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
package org.thingsboard.mqtt.broker.service.ttl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.dao.event.EventService;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.provider.AbstractServiceProvider;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EventsCleanUpService extends AbstractServiceProvider {

    static final String RANDOM_DELAY_INTERVAL_MS_EXPRESSION =
            "#{T(org.apache.commons.lang3.RandomUtils).nextLong(0, ${sql.ttl.events.execution_interval_ms})}";

    @Value("${sql.ttl.events.events_ttl}")
    private long ttlInSec;

    @Value("${sql.ttl.events.enabled}")
    private boolean ttlTaskExecutionEnabled;

    private final EventService eventService;

    public EventsCleanUpService(SystemInfoService systemInfoService,
                                ServiceInfoProvider serviceInfoProvider,
                                EventService eventService) {
        super(systemInfoService, serviceInfoProvider);
        this.eventService = eventService;
    }

    @Scheduled(initialDelayString = RANDOM_DELAY_INTERVAL_MS_EXPRESSION, fixedDelayString = "${sql.ttl.events.execution_interval_ms}")
    public void cleanUp() {
        if (ttlTaskExecutionEnabled) {
            long ts = System.currentTimeMillis();
            long eventExpTs = ttlInSec > 0 ? ts - TimeUnit.SECONDS.toMillis(ttlInSec) : 0;
            eventService.cleanupEvents(eventExpTs, isCurrentNodeShouldCleanUpEvents());
        }
    }

}
