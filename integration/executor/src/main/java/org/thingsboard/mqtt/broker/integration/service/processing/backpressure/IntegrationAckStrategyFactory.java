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
package org.thingsboard.mqtt.broker.integration.service.processing.backpressure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
import org.thingsboard.mqtt.broker.integration.api.data.IntegrationPackProcessingResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationAckStrategyFactory {

    private final IntegrationAckStrategyConfiguration ackStrategyConfiguration;

    public IntegrationAckStrategy newInstance(String integrationId) {
        return switch (ackStrategyConfiguration.getType()) {
            case SKIP_ALL -> new SkipStrategy(integrationId);
            case RETRY_ALL ->
                    new RetryStrategy(integrationId, ackStrategyConfiguration.getRetries(), ackStrategyConfiguration.getPauseBetweenRetries());
        };
    }

    @RequiredArgsConstructor
    private static class SkipStrategy implements IntegrationAckStrategy {

        private final String integrationId;

        @Override
        public IntegrationProcessingDecision analyze(IntegrationPackProcessingResult result) {
            if (!result.getPendingMap().isEmpty() || !result.getFailedMap().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Skip reprocess for {} failed and {} timeout messages.", integrationId, result.getFailedMap().size(), result.getPendingMap().size());
                }
            }
            if (log.isTraceEnabled()) {
                result.getFailedMap().forEach((packetId, msg) ->
                        log.trace("[{}] Failed message: id - {}, topic - {}.",
                                integrationId, msg.getPublishMsgProto().getPacketId(), msg.getPublishMsgProto().getTopicName())
                );
                result.getPendingMap().forEach((packetId, msg) ->
                        log.trace("[{}] Timeout message: id - {}, topic - {}.",
                                integrationId, msg.getPublishMsgProto().getPacketId(), msg.getPublishMsgProto().getTopicName())
                );
            }
            return new IntegrationProcessingDecision(true, Collections.emptyMap());
        }
    }

    @RequiredArgsConstructor
    private static class RetryStrategy implements IntegrationAckStrategy {

        private final String integrationId;
        private final int maxRetries;
        private final int pauseBetweenRetries;

        private int retryCount;

        @Override
        public IntegrationProcessingDecision analyze(IntegrationPackProcessingResult result) {
            Map<UUID, PublishIntegrationMsgProto> pendingMap = result.getPendingMap();
            Map<UUID, PublishIntegrationMsgProto> failedMap = result.getFailedMap();
            if (pendingMap.isEmpty() && failedMap.isEmpty()) {
                return new IntegrationProcessingDecision(true, Collections.emptyMap());
            }
            if (maxRetries != 0 && ++retryCount > maxRetries) {
                log.debug("[{}] Skip reprocess due to max retries.", integrationId);
                return new IntegrationProcessingDecision(true, Collections.emptyMap());
            }
            Map<UUID, PublishIntegrationMsgProto> toReprocess = new HashMap<>();
            toReprocess.putAll(pendingMap);
            toReprocess.putAll(failedMap);
            if (log.isDebugEnabled()) {
                log.debug("[{}] Going to reprocess {} messages", integrationId, toReprocess.size());
            }
            if (log.isTraceEnabled()) {
                failedMap.forEach((packetId, msg) ->
                        log.trace("[{}] Going to reprocess failed message: id - {}, topic - {}.",
                                integrationId, msg.getPublishMsgProto().getPacketId(), msg.getPublishMsgProto().getTopicName())
                );
                pendingMap.forEach((packetId, msg) ->
                        log.trace("[{}] Going to reprocess timed-out message: id - {}, topic - {}.",
                                integrationId, msg.getPublishMsgProto().getPacketId(), msg.getPublishMsgProto().getTopicName())
                );
            }
            if (pauseBetweenRetries > 0) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(pauseBetweenRetries));
                } catch (InterruptedException e) {
                    log.error("[{}] Failed to pause for retry", integrationId);
                }
            }
            return new IntegrationProcessingDecision(false, toReprocess);
        }
    }

}
