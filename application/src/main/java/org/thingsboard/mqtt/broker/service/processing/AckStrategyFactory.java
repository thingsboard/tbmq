/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AckStrategyFactory {
    private final AckStrategyConfiguration ackStrategyConfiguration;

    public AckStrategy newInstance(String consumerId) {
        switch (ackStrategyConfiguration.getType()) {
            case SKIP_ALL:
                return new SkipStrategy(consumerId);
            case RETRY_ALL:
                return new RetryStrategy(consumerId, ackStrategyConfiguration.getRetries());
            default:
                throw new RuntimeException("AckStrategy with type " + ackStrategyConfiguration.getType() + " is not supported!");
        }
    }

    @RequiredArgsConstructor
    private static class SkipStrategy implements AckStrategy {
        private final String consumerId;

        @Override
        public ProcessingDecision analyze(PackProcessingResult result) {
            if (!result.getPendingMap().isEmpty() || !result.getFailedMap().isEmpty()) {
                log.debug("[{}] Skip reprocess for {} failed and {} timeout messages.", consumerId, result.getFailedMap().size(), result.getPendingMap().size());
            }
            if (log.isTraceEnabled()) {
                result.getFailedMap().forEach((packetId, msg) ->
                        log.trace("[{}] Failed message: id - {}, topic - {}.",
                                consumerId, msg.getId(), msg.getPublishMsgProto().getTopicName())
                );
                result.getPendingMap().forEach((packetId, msg) ->
                        log.trace("[{}] Timeout message: id - {}, topic - {}.",
                                consumerId, msg.getId(), msg.getPublishMsgProto().getTopicName())
                );
            }
            return new ProcessingDecision(true, Collections.emptyMap());
        }
    }

    @RequiredArgsConstructor
    private static class RetryStrategy implements AckStrategy {
        private final String consumerId;
        private final int maxRetries;

        private int retryCount;

        @Override
        public ProcessingDecision analyze(PackProcessingResult result) {
            Map<UUID, PublishMsgWithId> pendingMap = result.getPendingMap();
            Map<UUID, PublishMsgWithId> failedMap = result.getFailedMap();
            if (pendingMap.isEmpty()) {
                return new ProcessingDecision(true, Collections.emptyMap());
            }
            if (maxRetries != 0 && ++retryCount > maxRetries) {
                log.debug("[{}] Skip reprocess due to max retries.", consumerId);
                return new ProcessingDecision(true, Collections.emptyMap());
            }
            ConcurrentMap<UUID, PublishMsgWithId> toReprocess = new ConcurrentHashMap<>();
            toReprocess.putAll(pendingMap);
            toReprocess.putAll(failedMap);
            log.debug("[{}] Going to reprocess {} messages", consumerId, toReprocess);
            if (log.isTraceEnabled()) {
                failedMap.forEach((packetId, msg) ->
                        log.trace("[{}] Going to reprocess failed message: id - {}, topic - {}.",
                                consumerId, msg.getId(), msg.getPublishMsgProto().getTopicName())
                );
                pendingMap.forEach((packetId, msg) ->
                        log.trace("[{}] Going to reprocess timed-out message: id - {}, topic - {}.",
                                consumerId, msg.getId(), msg.getPublishMsgProto().getTopicName())
                );
            }
            return new ProcessingDecision(false, toReprocess);
        }
    }
}
