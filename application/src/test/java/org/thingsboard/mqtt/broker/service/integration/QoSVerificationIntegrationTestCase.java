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
package org.thingsboard.mqtt.broker.service.integration;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = QoSVerificationIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.retransmission.enabled=true",
        "mqtt.retransmission.initial-delay=1",
        "mqtt.retransmission.period=1"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class QoSVerificationIntegrationTestCase extends AbstractQoSVerificationIntegrationTestCase {

    @Test
    public void qoS1DeliveryValidationTest() throws Throwable {
        process(QOS_1, true);
    }

    @Test
    public void qoS2DeliveryValidationTest() throws Throwable {
        process(QOS_2, true);
    }

    @Test
    public void qoS1PersistentDeliveryValidationTest() throws Throwable {
        process(QOS_1, false);
    }

    @Test
    public void qoS2PersistentDeliveryValidationTest() throws Throwable {
        process(QOS_2, false);
    }
}
