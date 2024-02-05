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
package org.thingsboard.mqtt.broker.service.integration.persistentsession;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.service.integration.parent.AbstractFlowControlIntegrationTestCase;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = AppFlowControlIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.max-in-flight-msgs=500",
        "security.mqtt.basic.enabled=true",
        "queue.kafka.application-persisted-msg.additional-consumer-config=fetch.min.bytes:1000"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class AppFlowControlIntegrationTestCase extends AbstractFlowControlIntegrationTestCase {

    private static final String APP_FLOW_CONTROL_CLIENT = "appFlowControlClient";

    @Before
    public void init() {
        super.init(TestUtils.createApplicationClientCredentials(null, FLOW_CONTROL_USER_NAME));
    }

    @After
    public void clear() throws Exception {
        super.clear();
    }

    @Test
    public void givenReceiveMaximumSetTo5_whenSend10MessagesWithoutAcks_thenReceive5Messages() throws Throwable {
        super.givenReceiveMaximumSetTo5_whenSend10MessagesWithoutAcks_thenReceive5Messages(APP_FLOW_CONTROL_CLIENT);
    }

    @Test
    public void givenReceiveMaximumSetTo1_whenSend2Messages_thenReceiveMessagesWithDelay() throws Throwable {
        super.givenReceiveMaximumSetTo1_whenSend2Messages_thenReceiveMessagesWithDelay(APP_FLOW_CONTROL_CLIENT, false);
    }

    @Test
    public void givenReceiveMaximumSetTo2_whenSend2Messages_thenReceiveMessagesWithoutDelay() throws Throwable {
        super.givenReceiveMaximumSetTo2_whenSend2Messages_thenReceiveMessagesWithoutDelay(APP_FLOW_CONTROL_CLIENT, false);
    }

}
