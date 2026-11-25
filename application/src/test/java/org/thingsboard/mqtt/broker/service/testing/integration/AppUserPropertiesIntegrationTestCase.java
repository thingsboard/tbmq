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
package org.thingsboard.mqtt.broker.service.testing.integration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.service.test.util.TestUtils;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = AppUserPropertiesIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@RunWith(SpringRunner.class)
public class AppUserPropertiesIntegrationTestCase extends AbstractUserPropertiesIntegrationTest {

    static final String PUB_CLIENT_ID_APP = "pubClientIdApp";
    static final String SUB_CLIENT_ID_APP = "subClientIdApp";

    @Before
    public void beforeAppTest() {
        saveCredentials(TestUtils.createApplicationClientCredentials(PUB_CLIENT_ID_APP, null));
        saveCredentials(TestUtils.createApplicationClientCredentials(SUB_CLIENT_ID_APP, null));
    }

    @Test
    public void testUserPropertiesAppPersistedSession() throws Throwable {
        processTest(SUB_CLIENT_ID_APP, PUB_CLIENT_ID_APP, false, 2);
    }

}
