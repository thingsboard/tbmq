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
package org.thingsboard.mqtt.broker.service.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceStatusTest {

    @Test
    void testServiceStatusFromLastUpdateTime() {
        long now = System.currentTimeMillis();

        assertEquals(ServiceStatus.ACTIVE, ServiceStatus.fromLastUpdateTime(now - 1000));
        assertEquals(ServiceStatus.INACTIVE, ServiceStatus.fromLastUpdateTime(now - (2 * 60 * 60 * 1000)));
        assertEquals(ServiceStatus.OUTDATED, ServiceStatus.fromLastUpdateTime(now - (8L * 24 * 60 * 60 * 1000)));
    }

}
