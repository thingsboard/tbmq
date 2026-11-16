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
package org.thingsboard.mqtt.broker.lite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.thingsboard.mqtt.broker.controller.LookupEndpointsControllerTest;
import org.thingsboard.mqtt.broker.controller.MqttAuthProviderControllerTest;
import org.thingsboard.mqtt.broker.controller.MqttClientCredentialsControllerTest;
import org.thingsboard.mqtt.broker.controller.MqttPublishControllerTest;

/**
 * The application's controller tests that apply to Lite, on the Lite classpath. Not run here:
 * {@code IntegrationControllerTest} and {@code NodeDrainControllerTest} (Lite has neither integrations nor node
 * drain), and {@code OpenApiSpecTest} (it checks the cluster edition's spec).
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        LookupEndpointsControllerTest.class,
        MqttAuthProviderControllerTest.class,
        MqttClientCredentialsControllerTest.class,
        MqttPublishControllerTest.class,
})
public class LiteControllerTestSuite {

}
