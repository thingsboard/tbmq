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
package org.thingsboard.mqtt.broker.common.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.HISTORICAL_KEYS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MSG_RELATED_HISTORICAL_KEYS;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.PROCESSED_BYTES;

class BrokerConstantsTest {

    @Test
    void givenProcessedBytes_whenCheckingHistoricalKeyLists_thenNotPresentInEither() {
        // processedBytes was always persisted as 0 and never incremented (dead key). It must no
        // longer be persisted per-interval nor queried as a latest value.
        assertFalse(MSG_RELATED_HISTORICAL_KEYS.contains(PROCESSED_BYTES),
                "processedBytes must not be a per-interval persisted historical key");
        assertFalse(HISTORICAL_KEYS.contains(PROCESSED_BYTES),
                "processedBytes must not be a queried latest historical key");
    }
}
