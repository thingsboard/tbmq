/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.limits;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.service.session.ClientSessionService;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = RateLimitScheduledService.class)
public class RateLimitScheduledServiceTest {

    @MockBean
    private ClientSessionService clientSessionService;
    @MockBean
    private RateLimitCacheService rateLimitCacheService;

    @SpyBean
    private RateLimitScheduledService rateLimitScheduledService;

    @Test
    public void testScheduleSessionsLimitCorrection() {
        when(clientSessionService.getClientSessionsCount()).thenReturn(5);

        rateLimitScheduledService.scheduleSessionsLimitCorrection();

        verify(rateLimitCacheService, atLeastOnce()).setSessionCount(5);
    }

}
