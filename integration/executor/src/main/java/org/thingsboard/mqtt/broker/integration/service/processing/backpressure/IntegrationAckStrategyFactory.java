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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationPackProcessingResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationAckStrategyFactory {

    private final IntegrationAckStrategyConfiguration ackStrategyConfiguration;
    private final IntegrationEventAckStrategyConfiguration eventAckStrategyConfiguration;

    public <T> IntegrationAckStrategy<T> newInstance(String integrationId) {
        return build(integrationId, ackStrategyConfiguration.getType(),
                ackStrategyConfiguration.getRetries(), ackStrategyConfiguration.getPauseBetweenRetries());
    }

    public <T> IntegrationAckStrategy<T> newEventInstance(String integrationId) {
        return build(integrationId, eventAckStrategyConfiguration.getType(),
                eventAckStrategyConfiguration.getRetries(), eventAckStrategyConfiguration.getPauseBetweenRetries());
    }

    private <T> IntegrationAckStrategy<T> build(String integrationId, IntegrationAckStrategyType type, int retries, int pauseBetweenRetries) {
        return switch (type) {
            case SKIP_ALL -> new SkipStrategy<>(integrationId);
            case RETRY_ALL -> new RetryStrategy<>(integrationId, retries, pauseBetweenRetries);
        };
    }

    @RequiredArgsConstructor
    private static class SkipStrategy<T> implements IntegrationAckStrategy<T> {

        private final String integrationId;

        @Override
        public IntegrationProcessingDecision<T> analyze(IntegrationPackProcessingResult<T> result) {
            if (!result.getPendingMap().isEmpty() || !result.getFailedMap().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Skip reprocess for {} failed and {} timeout messages.", integrationId, result.getFailedMap().size(), result.getPendingMap().size());
                }
            }
            if (log.isTraceEnabled()) {
                result.getFailedMap().forEach((packetId, msg) ->
                        log.trace("[{}] Failed message: id - {}.", integrationId, packetId)
                );
                result.getPendingMap().forEach((packetId, msg) ->
                        log.trace("[{}] Timeout message: id - {}.", integrationId, packetId)
                );
            }
            return new IntegrationProcessingDecision<>(true, Collections.emptyMap());
        }
    }

    @RequiredArgsConstructor
    private static class RetryStrategy<T> implements IntegrationAckStrategy<T> {

        private final String integrationId;
        private final int maxRetries;
        private final int pauseBetweenRetries;

        private int retryCount;

        @Override
        public IntegrationProcessingDecision<T> analyze(IntegrationPackProcessingResult<T> result) {
            Map<UUID, T> pendingMap = result.getPendingMap();
            Map<UUID, T> failedMap = result.getFailedMap();
            if (pendingMap.isEmpty() && failedMap.isEmpty()) {
                return new IntegrationProcessingDecision<>(true, Collections.emptyMap());
            }
            if (maxRetries != 0 && ++retryCount > maxRetries) {
                log.debug("[{}] Skip reprocess due to max retries.", integrationId);
                return new IntegrationProcessingDecision<>(true, Collections.emptyMap());
            }
            // The pending/failed maps are unordered (ConcurrentHashMap -> HashMap copies), but the keys are
            // UUID(packId, index), so sorting by key restores the original consume/offset order for the retry pass.
            Map<UUID, T> toReprocess = Stream.concat(pendingMap.entrySet().stream(), failedMap.entrySet().stream())
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (a, b) -> a, LinkedHashMap::new));
            if (log.isDebugEnabled()) {
                log.debug("[{}] Going to reprocess {} messages", integrationId, toReprocess.size());
            }
            if (log.isTraceEnabled()) {
                failedMap.forEach((packetId, msg) ->
                        log.trace("[{}] Going to reprocess failed message: id - {}.", integrationId, packetId)
                );
                pendingMap.forEach((packetId, msg) ->
                        log.trace("[{}] Going to reprocess timed-out message: id - {}.", integrationId, packetId)
                );
            }
            if (pauseBetweenRetries > 0) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(pauseBetweenRetries));
                } catch (InterruptedException e) {
                    log.error("[{}] Failed to pause for retry", integrationId);
                }
            }
            return new IntegrationProcessingDecision<>(false, toReprocess);
        }
    }

}
