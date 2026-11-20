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
package org.thingsboard.mqtt.broker.dao.client.unauthorized;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.dao.clock.CleanupClockGuard;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnauthorizedClientServiceImplCleanUpTest {

    private static final long TTL_SEC = 259200;

    @Mock
    private UnauthorizedClientDao unauthorizedClientDao;
    @Mock
    private CleanupClockGuard cleanupClockGuard;

    private UnauthorizedClientServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UnauthorizedClientServiceImpl(unauthorizedClientDao, cleanupClockGuard);
        ReflectionTestUtils.setField(service, "ttlInSec", TTL_SEC);
        ReflectionTestUtils.setField(service, "ttlTaskExecutionEnabled", true);
    }

    @Test
    void givenAnAllowingClockGuard_whenCleanUp_thenTheExpiredClientsAreDeleted() {
        when(cleanupClockGuard.cleanupAllowed()).thenReturn(true);

        service.cleanUp();

        verify(unauthorizedClientDao).cleanupUnauthorizedClients(TTL_SEC);
    }

    @Test
    void givenAPostponingClockGuard_whenCleanUp_thenNothingIsDeleted() {
        when(cleanupClockGuard.cleanupAllowed()).thenReturn(false);

        service.cleanUp();

        verify(unauthorizedClientDao, never()).cleanupUnauthorizedClients(anyLong());
    }

}
