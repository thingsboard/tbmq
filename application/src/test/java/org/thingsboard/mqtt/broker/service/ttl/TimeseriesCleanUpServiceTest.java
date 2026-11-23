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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.dao.clock.CleanupClockGuard;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.system.SystemInfoService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeseriesCleanUpServiceTest {

    private static final long TTL_SEC = 604800;

    @Mock
    private SystemInfoService systemInfoService;
    @Mock
    private ServiceInfoProvider serviceInfoProvider;
    @Mock
    private TimeseriesService timeseriesService;
    @Mock
    private CleanupClockGuard cleanupClockGuard;

    private TimeseriesCleanUpService service;

    @BeforeEach
    void setUp() {
        service = new TimeseriesCleanUpService(systemInfoService, serviceInfoProvider, timeseriesService, cleanupClockGuard);
        ReflectionTestUtils.setField(service, "systemTtl", TTL_SEC);
        ReflectionTestUtils.setField(service, "ttlTaskExecutionEnabled", true);
    }

    @Test
    void givenAnAllowingClockGuard_whenCleanUp_thenTheTimeseriesAreCleanedUp() {
        when(cleanupClockGuard.cleanupAllowed()).thenReturn(true);
        when(serviceInfoProvider.getServiceId()).thenReturn("tbmq-lite");
        when(systemInfoService.getTbmqServiceIds()).thenReturn(List.of("tbmq-lite"));

        service.cleanUp();

        verify(timeseriesService).cleanUp(TTL_SEC);
    }

    @Test
    void givenAPostponingClockGuard_whenCleanUp_thenTheTimeseriesAreNotCleanedUp() {
        when(cleanupClockGuard.cleanupAllowed()).thenReturn(false);

        service.cleanUp();

        verify(timeseriesService, never()).cleanUp(anyLong());
    }

}
