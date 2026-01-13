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
package org.thingsboard.mqtt.broker.integration.service.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.mqtt.broker.common.data.limit.LimitedApi;
import org.thingsboard.mqtt.broker.exception.TbRateLimitsException;
import org.thingsboard.mqtt.broker.integration.service.limit.RateLimitService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultIntegrationRateLimitServiceTest {

    @Mock
    private RateLimitService rateLimitService;

    private DefaultIntegrationRateLimitService rateLimitServiceImpl;
    private UUID integrationId;

    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        rateLimitServiceImpl = new DefaultIntegrationRateLimitService(rateLimitService);

        // Set private field values for testing
        ReflectionTestUtils.setField(rateLimitServiceImpl, "eventRateLimitsEnabled", true);
        ReflectionTestUtils.setField(rateLimitServiceImpl, "integrationEventsRateLimitsConf", "100:1,1000:60");
        ReflectionTestUtils.setField(rateLimitServiceImpl, "deduplicationDurationMs", 60000L);

        integrationId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testInit_WhenRateLimitEnabled() {
        rateLimitServiceImpl.init();
        verify(rateLimitService, times(1)).initIntegrationRateLimit("100:1,1000:60");
    }

    @Test
    void testInit_WhenRateLimitDisabled() {
        ReflectionTestUtils.setField(rateLimitServiceImpl, "eventRateLimitsEnabled", false);

        rateLimitServiceImpl.init();

        verify(rateLimitService, never()).initIntegrationRateLimit(anyString());
    }

    @Test
    void testCheckLimit_WhenRateLimitDisabled() {
        ReflectionTestUtils.setField(rateLimitServiceImpl, "eventRateLimitsEnabled", false);

        boolean result = rateLimitServiceImpl.checkLimit(integrationId, false);

        assertTrue(result);
        verify(rateLimitService, never()).checkRateLimit(any());
    }

    @Test
    void testCheckLimit_WhenRateLimitAllowed() {
        when(rateLimitService.checkRateLimit(LimitedApi.INTEGRATION_EVENTS)).thenReturn(true);

        boolean result = rateLimitServiceImpl.checkLimit(integrationId, false);

        assertTrue(result);
        verify(rateLimitService, times(1)).checkRateLimit(LimitedApi.INTEGRATION_EVENTS);
    }

    @Test
    void testCheckLimit_WhenRateLimitExceeded_WithoutException() {
        when(rateLimitService.checkRateLimit(LimitedApi.INTEGRATION_EVENTS)).thenReturn(false);

        boolean result = rateLimitServiceImpl.checkLimit(integrationId, false);

        assertFalse(result);
        verify(rateLimitService, times(1)).checkRateLimit(LimitedApi.INTEGRATION_EVENTS);
    }

    @Test
    void testCheckLimit_WhenRateLimitExceeded_WithException() {
        when(rateLimitService.checkRateLimit(LimitedApi.INTEGRATION_EVENTS)).thenReturn(false);

        TbRateLimitsException exception = assertThrows(TbRateLimitsException.class, () ->
                rateLimitServiceImpl.checkLimit(integrationId, true));

        assertEquals("Integration error rate limits reached!", exception.getMessage());
        verify(rateLimitService, times(1)).checkRateLimit(LimitedApi.INTEGRATION_EVENTS);
    }

}
