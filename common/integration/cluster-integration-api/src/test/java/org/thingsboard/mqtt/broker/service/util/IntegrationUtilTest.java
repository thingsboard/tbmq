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
package org.thingsboard.mqtt.broker.service.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntegrationUtilTest {

    private static final String ID = "1d2f5d40-1111-2222-3333-444455556666";

    @Test
    void givenIntegrationId_whenGetIntegrationEventTopic_thenPrefixedAndHyphensRemoved() {
        assertThat(IntegrationUtil.getIntegrationEventTopic(ID))
                .isEqualTo("tbmq.ie.event.1d2f5d40111122223333444455556666");
    }

    @Test
    void givenIntegrationId_whenGetIntegrationEventConsumerGroup_thenPrefixedAndHyphensRemoved() {
        assertThat(IntegrationUtil.getIntegrationEventConsumerGroup(ID))
                .isEqualTo("ie-event-consumer-group-1d2f5d40111122223333444455556666");
    }
}
