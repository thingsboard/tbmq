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
package org.thingsboard.mqtt.broker.service.testing.integration.persistentsession;

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
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;
import org.thingsboard.mqtt.broker.service.testing.integration.parent.AbstractFlowControlIntegrationTestCase;

@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = DeviceFlowControlIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.max-in-flight-msgs=500",
        "security.mqtt.basic.enabled=true"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class DeviceFlowControlIntegrationTestCase extends AbstractFlowControlIntegrationTestCase {

    private static final String DEV_FLOW_CONTROL_CLIENT = "devFlowControlClient";

    @Before
    public void init() {
        super.init(TestUtils.createDeviceClientCredentials(null, FLOW_CONTROL_USER_NAME));
    }

    @After
    public void clear() throws Exception {
        super.clear();
    }

    @Test
    public void givenReceiveMaximumSetTo5_whenSend10MessagesWithoutAcks_thenReceive5Messages() throws Throwable {
        super.givenReceiveMaximumSetTo5_whenSend10MessagesWithoutAcks_thenReceive5Messages(DEV_FLOW_CONTROL_CLIENT);
    }

    @Test
    public void givenReceiveMaximumSetTo1_whenSend2Messages_thenReceiveMessagesWithDelay() throws Throwable {
        super.givenReceiveMaximumSetTo1_whenSend2Messages_thenReceiveMessagesWithDelay(DEV_FLOW_CONTROL_CLIENT, false);
    }

    @Test
    public void givenReceiveMaximumSetTo2_whenSend2Messages_thenReceiveMessagesWithoutDelay() throws Throwable {
        super.givenReceiveMaximumSetTo2_whenSend2Messages_thenReceiveMessagesWithoutDelay(DEV_FLOW_CONTROL_CLIENT, false);
    }

}
