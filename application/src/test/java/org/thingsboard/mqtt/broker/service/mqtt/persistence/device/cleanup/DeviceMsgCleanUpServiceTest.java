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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.cleanup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.messages.sql.SqlDeviceMsgCleanUpDao;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@RunWith(SpringRunner.class)
@EnableScheduling
@ContextConfiguration(classes = DeviceMsgCleanUpService.class)
@TestPropertySource(properties = {
        "mqtt.persistent-session.device.persisted-messages.ttl=100000",
        "mqtt.persistent-session.device.persisted-messages.limit=100",
        "mqtt.persistent-session.device.persisted-messages.clean-up.cron=* * * * * *",
        "mqtt.persistent-session.device.persisted-messages.clean-up.zone=UTC"
})
public class DeviceMsgCleanUpServiceTest {

    @MockBean
    SqlDeviceMsgCleanUpDao cleanUpDao;

    @SpyBean
    DeviceMsgCleanUpService deviceMsgCleanUpService;

    @Test
    public void whenWaitThreeSeconds_thenScheduledIsCalledAtLeastOneTime() {
        await()
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(deviceMsgCleanUpService, atLeast(1)).cleanUp());

        verify(cleanUpDao, atLeast(1)).cleanUpBySize(eq(100));
        verify(cleanUpDao, atLeast(1)).cleanUpByTime(eq(100000L));
    }
}