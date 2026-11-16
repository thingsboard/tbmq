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
 * The application's integration suite on the Lite classpath. Its test classes come from the application's test-jar,
 * so the suite scans jars too. Every excluded class is named with the reason it cannot run in Lite.
 */
@RunWith(ClasspathSuite.class)
@ClasspathSuite.IncludeJars(true)
@ClasspathSuite.ClassnameFilters({
        "org.thingsboard.mqtt.broker.service.testing.integration.*TestCase",
        // APPLICATION clients and application shared subscriptions: Lite has neither
        "!.*AppBackpressureIntegrationTestCase",
        "!.*AppClientsLimitIntegrationTestCase",
        "!.*AppFlowControlIntegrationTestCase",
        "!.*AppMsgExpiryIntegrationTestCase",
        "!.*AppPayloadFormatAndContentTypesIntegrationTestCase",
        "!.*AppPersistedMessagesIntegrationTestCase",
        "!.*AppPersistedSessionIntegrationTestCase",
        "!.*AppRequestResponseIntegrationTestCase",
        "!.*AppSharedSubscriptionsIntegrationTestCase",
        "!.*TotalThroughputQuotaAppPersistedIntegrationTestCase",
        "!.*TotalThroughputQuotaSharedGroupIntegrationTestCase",
        // needs Kafka: it fails the Kafka producer; its Lite counterpart fails the store instead
        "!.*PersistFailurePubResponseIntegrationTestCase",
        // needs Valkey: it reads the rate-limit buckets in Valkey, or stops Valkey
        "!.*RateLimitBucketConfigurationIntegrationTestCase",
        "!.*TotalThroughputQuotaRedisOutageIntegrationTestCase",
})
public class LiteIntegrationTestSuite {

}
