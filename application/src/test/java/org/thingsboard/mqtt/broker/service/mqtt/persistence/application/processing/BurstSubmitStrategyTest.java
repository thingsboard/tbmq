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

    @Test
    void update_multiMessage_preservesOrderAndSubstitutesBothDupCopies() {
        PublishMsg msg1 = new PublishMsg(1, "test/topic", new byte[]{1}, 1, false, false);
        PublishMsg msg2 = new PublishMsg(2, "test/topic", new byte[]{2}, 1, false, false);
        PersistedPublishMsg original1 = new PersistedPublishMsg(msg1, 10L, false);
        PersistedPublishMsg original2 = new PersistedPublishMsg(msg2, 20L, false);

        BurstSubmitStrategy strategy = new BurstSubmitStrategy("appClient");
        strategy.init(List.of(original1, original2));

        PersistedPublishMsg dup1 = original1.toBuilder()
                .publishMsg(original1.getPublishMsg().toBuilder().isDup(true).build())
                .build();
        PersistedPublishMsg dup2 = original2.toBuilder()
                .publishMsg(original2.getPublishMsg().toBuilder().isDup(true).build())
                .build();

        strategy.update(Map.of(1, dup1, 2, dup2));

        List<PersistedMsg> reprocessed = strategy.getOrderedMessages();
        assertThat(reprocessed).hasSize(2);
        assertThat(reprocessed.get(0).getPacketId())
                .as("first message must retain packetId 1 (original insertion order preserved)")
                .isEqualTo(1);
        assertThat(reprocessed.get(1).getPacketId())
                .as("second message must retain packetId 2 (original insertion order preserved)")
                .isEqualTo(2);
        assertThat(reprocessed.get(0).getPublishMsg().isDup()).as("first message must carry DUP=1").isTrue();
        assertThat(reprocessed.get(1).getPublishMsg().isDup()).as("second message must carry DUP=1").isTrue();
        assertThat(reprocessed.get(0)).as("update() must use dup1 copy for packetId 1").isSameAs(dup1);
        assertThat(reprocessed.get(1)).as("update() must use dup2 copy for packetId 2").isSameAs(dup2);
    }

    @Test
    void update_dropFiltering_dropsMessageAbsentFromReprocessMap() {
        PublishMsg msg1 = new PublishMsg(1, "test/topic", new byte[]{1}, 1, false, false);
        PublishMsg msg2 = new PublishMsg(2, "test/topic", new byte[]{2}, 1, false, false);
        PersistedPublishMsg original1 = new PersistedPublishMsg(msg1, 10L, false);
        PersistedPublishMsg original2 = new PersistedPublishMsg(msg2, 20L, false);

        BurstSubmitStrategy strategy = new BurstSubmitStrategy("appClient");
        strategy.init(List.of(original1, original2));

        // Reprocess map contains only packetId 1; packetId 2 is absent and must be dropped.
        PersistedPublishMsg dup1 = original1.toBuilder()
                .publishMsg(original1.getPublishMsg().toBuilder().isDup(true).build())
                .build();

        strategy.update(Map.of(1, dup1));

        List<PersistedMsg> reprocessed = strategy.getOrderedMessages();
        assertThat(reprocessed).hasSize(1);
        assertThat(reprocessed.get(0).getPacketId())
                .as("only the message present in the reprocess map must survive")
                .isEqualTo(1);
    }
}
