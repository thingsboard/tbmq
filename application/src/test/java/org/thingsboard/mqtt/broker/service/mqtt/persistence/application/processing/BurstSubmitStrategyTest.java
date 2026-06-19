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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BurstSubmitStrategyTest {

    @Test
    void update_rebuildsFromDupFlaggedReprocessCopies_notTheOriginalMessages() {
        PublishMsg publishMsg = new PublishMsg(1, "test/topic", new byte[]{1}, 1, false, false);
        PersistedPublishMsg original = new PersistedPublishMsg(publishMsg, 10L, false);

        BurstSubmitStrategy strategy = new BurstSubmitStrategy("appClient");
        strategy.init(List.of(original));

        // The RetryStrategy puts a DUP-flagged copy (same packetId) into the reprocess map.
        PersistedPublishMsg dupCopy = original.toBuilder()
                .publishMsg(original.getPublishMsg().toBuilder().isDup(true).build())
                .build();

        strategy.update(Map.of(1, dupCopy));

        List<PersistedMsg> reprocessed = strategy.getOrderedMessages();
        assertThat(reprocessed).hasSize(1);
        assertThat(reprocessed.get(0).getPublishMsg().isDup())
                .as("retransmitted QoS1 PUBLISH must carry DUP=1 (MQTT 3.3.1.1)")
                .isTrue();
        assertThat(reprocessed.get(0))
                .as("update() must use the reprocess-map copy, not the original message")
                .isSameAs(dupCopy);
    }
}
