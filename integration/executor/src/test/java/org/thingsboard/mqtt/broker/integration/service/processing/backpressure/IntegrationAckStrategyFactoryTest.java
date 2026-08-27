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
package org.thingsboard.mqtt.broker.integration.service.processing.backpressure;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationPackProcessingContext;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationPackProcessingResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationAckStrategyFactoryTest {

    @Test
    void givenDataRetryAndEventSkip_whenAnalyzeUnprocessedPack_thenDataReprocessesAndEventCommits() {
        IntegrationAckStrategyConfiguration dataConfig = new IntegrationAckStrategyConfiguration();
        dataConfig.setType(IntegrationAckStrategyType.RETRY_ALL);
        dataConfig.setRetries(1);
        dataConfig.setPauseBetweenRetries(0);

        IntegrationEventAckStrategyConfiguration eventConfig = new IntegrationEventAckStrategyConfiguration();
        eventConfig.setType(IntegrationAckStrategyType.SKIP_ALL);

        IntegrationAckStrategyFactory factory = new IntegrationAckStrategyFactory(dataConfig, eventConfig);

        // data stream (RETRY_ALL) must NOT commit an unprocessed pack - it reprocesses...
        assertFalse(factory.newInstance("id").analyze(resultWithPending()).isCommit());
        // ...while the events stream (SKIP_ALL) commits (drops) it, independent of the data strategy.
        assertTrue(factory.newEventInstance("id").analyze(resultWithPending()).isCommit());
    }

    @Test
    void givenUnorderedPendingAndFailed_whenRetryAnalyze_thenReprocessMapPreservesOffsetOrder() {
        IntegrationAckStrategyConfiguration dataConfig = new IntegrationAckStrategyConfiguration();
        dataConfig.setType(IntegrationAckStrategyType.RETRY_ALL);
        dataConfig.setRetries(1);
        dataConfig.setPauseBetweenRetries(0);

        IntegrationAckStrategyFactory factory =
                new IntegrationAckStrategyFactory(dataConfig, new IntegrationEventAckStrategyConfiguration());

        // Keys are UUID(packId, offsetIndex); insert in shuffled order into an unordered ConcurrentHashMap.
        long packId = 7L;
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        for (int i : new int[]{3, 1, 5, 0, 4, 2}) {
            pending.put(new UUID(packId, i), "m" + i);
        }
        IntegrationPackProcessingContext<Object> ctx = new IntegrationPackProcessingContext<>("id", pending);
        // Move a couple of entries into the failed map so reprocess spans both pending and failed.
        ctx.onFailure(new UUID(packId, 1));
        ctx.onFailure(new UUID(packId, 0));

        IntegrationProcessingDecision<Object> decision =
                factory.<Object>newInstance("id").analyze(new IntegrationPackProcessingResult<>(ctx));

        assertFalse(decision.isCommit());
        List<Long> reprocessOrder = decision.getReprocessMap().keySet().stream()
                .map(UUID::getLeastSignificantBits)
                .toList();
        assertIterableEquals(List.of(0L, 1L, 2L, 3L, 4L, 5L), reprocessOrder);
    }

    private static IntegrationPackProcessingResult<Object> resultWithPending() {
        ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
        pending.put(UUID.randomUUID(), new Object());
        return new IntegrationPackProcessingResult<>(new IntegrationPackProcessingContext<>("id", pending));
    }
}
