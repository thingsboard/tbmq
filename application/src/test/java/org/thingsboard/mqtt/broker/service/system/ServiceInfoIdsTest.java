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
package org.thingsboard.mqtt.broker.service.system;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.service.system.ServiceInfoIds.RegistryEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.UNKNOWN;
import static org.thingsboard.mqtt.broker.common.data.queue.ServiceType.TBMQ;
import static org.thingsboard.mqtt.broker.common.data.queue.ServiceType.TBMQ_INTEGRATION_EXECUTOR;

class ServiceInfoIdsTest {

    @Test
    void givenServiceTypes_whenRegistryField_thenBrokersStayBareAndExecutorsArePrefixed() {
        assertEquals("node1", ServiceInfoIds.registryField("node1", TBMQ.name()));
        assertEquals("TBMQ_INTEGRATION_EXECUTOR:node1", ServiceInfoIds.registryField("node1", TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    void givenServiceTypes_whenTimeseriesEntityId_thenItFollowsTheRegistryField() {
        assertEquals("node1", ServiceInfoIds.timeseriesEntityId("node1", TBMQ.name()));
        assertEquals("TBMQ_INTEGRATION_EXECUTOR:node1", ServiceInfoIds.timeseriesEntityId("node1", TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    void givenRegistryFields_whenParsed_thenTypedAndBareFieldsAreUnderstood() {
        assertEquals(new RegistryEntry("node1", TBMQ.name()), ServiceInfoIds.parseRegistryField("node1", TBMQ.name()));
        assertEquals(new RegistryEntry("node1", TBMQ_INTEGRATION_EXECUTOR.name()),
                ServiceInfoIds.parseRegistryField("TBMQ_INTEGRATION_EXECUTOR:node1", TBMQ_INTEGRATION_EXECUTOR.name()));
        assertEquals(new RegistryEntry("host:1883", TBMQ.name()), ServiceInfoIds.parseRegistryField("host:1883", TBMQ.name()));
        assertEquals(new RegistryEntry("node1", UNKNOWN), ServiceInfoIds.parseRegistryField("node1", null));
    }

    @Test
    void givenRegistryField_whenParsedBack_thenItRoundTrips() {
        for (String type : new String[]{TBMQ.name(), TBMQ_INTEGRATION_EXECUTOR.name()}) {
            assertEquals(new RegistryEntry("a:b", type), ServiceInfoIds.parseRegistryField(ServiceInfoIds.registryField("a:b", type), type));
        }
    }
}
