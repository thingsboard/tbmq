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

import org.junit.extensions.cpsuite.ClasspathSuite;
import org.junit.runner.RunWith;

/**
 * The application's controller tests on the Lite classpath. Like the integration suite, it selects them by name from
 * the application's test-jar, so a new controller test runs here too unless it is excluded below with its reason.
 * {@code OpenApiSpecTest} is not a {@code *ControllerTest}, so it is not picked up: it checks the cluster edition's
 * spec.
 */
@RunWith(ClasspathSuite.class)
@ClasspathSuite.IncludeJars(true)
@ClasspathSuite.ClassnameFilters({
        "org.thingsboard.mqtt.broker.controller.*ControllerTest",
        // Lite has neither integrations nor node drain
        "!.*IntegrationControllerTest",
        "!.*NodeDrainControllerTest",
        // application shared subscriptions: Lite has none
        "!.*AppSharedSubscriptionLookupControllerTest",
})
public class LiteControllerTestSuite {

}
