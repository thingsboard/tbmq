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
package org.thingsboard.mqtt.broker.service.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.ClientSubscriptionService;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.integration.IntegrationSubscriptionUpdateService;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.service.notification.InternodeNotificationsService;
import org.thingsboard.mqtt.broker.service.queue.IntegrationTopicService;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Guards the initial delay on the cleanup sweep. Spring registers {@code @Scheduled} tasks on ContextRefreshedEvent
 * and, with no initial delay, submits the first fixed-rate run right there - strictly before the ApplicationReadyEvent
 * that drives BrokerInitializer, so the sweep would read node-local state that is not loaded yet. Without the delay
 * the sweep below fires twice inside this test's window; with it, not at all.
 * <p>
 * There is no Spring context in {@link IntegrationCleanupServiceImplTest}, so only a context-backed test can observe
 * how the annotation is actually scheduled.
 */
@RunWith(SpringRunner.class)
@EnableScheduling
@ContextConfiguration(classes = IntegrationCleanupServiceImpl.class)
@TestPropertySource(properties = "integrations.cleanup.period=1")
public class IntegrationCleanupServiceSchedulingTest {

    /**
     * Two periods of the one-second {@code integrations.cleanup.period} set above, so an undeferred sweep is observed
     * more than once rather than raced against.
     */
    private static final long OBSERVED_WINDOW_SEC = 2;

    @MockitoBean
    IntegrationService integrationService;
    @MockitoBean
    IntegrationTopicService integrationTopicService;
    @MockitoBean
    IntegrationSubscriptionUpdateService integrationSubscriptionUpdateService;
    @MockitoBean
    IntegrationLifecycleEventTypeCache lifecycleEventTypeCache;
    @MockitoBean
    InternodeNotificationsService internodeNotificationsService;
    @MockitoBean
    IntegrationExpiryChecker expiryChecker;
    @MockitoBean
    ClientSubscriptionService clientSubscriptionService;

    @Test
    public void givenPeriodElapsedSinceContextRefresh_whenSchedulingRegistered_thenTheFirstSweepIsStillDeferred() {
        // Observing the deferral is only meaningful while the delay outlasts the window watched below - otherwise a
        // shortened delay would make this test pass vacuously instead of failing.
        assertThat(IntegrationCleanupServiceImpl.INITIAL_DELAY_SEC).isGreaterThan(OBSERVED_WINDOW_SEC);

        // Both of the sweep's first two collaborators are asserted on, so the test does not silently depend on which
        // of the two early-return checks cleanUp performs first.
        await().pollDelay(OBSERVED_WINDOW_SEC, TimeUnit.SECONDS)
                .atMost(OBSERVED_WINDOW_SEC + 3, TimeUnit.SECONDS)
                .untilAsserted(() -> verifyNoInteractions(expiryChecker, clientSubscriptionService));
    }

}
