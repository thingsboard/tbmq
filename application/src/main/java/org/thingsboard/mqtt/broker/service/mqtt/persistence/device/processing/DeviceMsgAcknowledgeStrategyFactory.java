/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceMsgAcknowledgeStrategyFactory {

    private final DeviceAckStrategyConfiguration ackStrategyConfiguration;

    public DeviceAckStrategy newInstance(String consumerId) {
        return switch (ackStrategyConfiguration.getType()) {
            case SKIP_ALL -> new SkipStrategy(consumerId);
            case RETRY_ALL -> new RetryStrategy(consumerId, ackStrategyConfiguration);
            default ->
                    throw new RuntimeException("AckStrategy with type " + ackStrategyConfiguration.getType() + " is not supported!");
        };
    }

    @RequiredArgsConstructor
    private static class SkipStrategy implements DeviceAckStrategy {
        private final String consumerId;

        @Override
        public DeviceProcessingDecision analyze(DevicePackProcessingResult result) {
            if (!result.getPendingMap().isEmpty() || !result.getFailedMap().isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Skip reprocess for {} failed and {} timeout messages.", consumerId, result.getFailedMap().size(), result.getPendingMap().size());
                }
            }
            if (log.isTraceEnabled()) {
                result.getFailedMap().forEach((clientId, pack) ->
                        log.trace("[{}] Failed messages: clientId - {}, pack size - {}.",
                                consumerId, clientId, pack.messages().size())
                );
                result.getPendingMap().forEach((clientId, pack) ->
                        log.trace("[{}] Timeout messages: clientId - {}, pack size - {}.",
                                consumerId, clientId, pack.messages().size())
                );
            }
            return new DeviceProcessingDecision(true, Collections.emptyMap());
        }
    }

    private static class RetryStrategy implements DeviceAckStrategy {
        private final String consumerId;
        private final int maxRetries;
        private final int pauseBetweenRetries;

        public RetryStrategy(String consumerId, DeviceAckStrategyConfiguration configuration) {
            this.consumerId = consumerId;
            this.maxRetries = configuration.getRetries();
            this.pauseBetweenRetries = configuration.getPauseBetweenRetries();
        }

        private int retryCount;

        @Override
        public DeviceProcessingDecision analyze(DevicePackProcessingResult result) {
            Map<String, ClientIdMessagesPack> pendingMap = result.getPendingMap();
            Map<String, ClientIdMessagesPack> failedMap = result.getFailedMap();
            if (pendingMap.isEmpty() && failedMap.isEmpty()) {
                return new DeviceProcessingDecision(true, Collections.emptyMap());
            }
            if (maxRetries != 0 && ++retryCount > maxRetries) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Skip reprocess due to max retries.", consumerId);
                }
                return new DeviceProcessingDecision(true, Collections.emptyMap());
            }
            ConcurrentMap<String, ClientIdMessagesPack> toReprocess = new ConcurrentHashMap<>();
            toReprocess.putAll(pendingMap);
            toReprocess.putAll(failedMap);
            if (log.isDebugEnabled()) {
                log.debug("[{}] Going to reprocess {} packs", consumerId, toReprocess);
            }
            if (log.isTraceEnabled()) {
                failedMap.forEach((clientId, pack) ->
                        log.trace("[{}] Going to reprocess failed messages: clientId - {}, pack size - {}.",
                                consumerId, clientId, pack.messages().size())
                );
                pendingMap.forEach((clientId, pack) ->
                        log.trace("[{}] Going to reprocess timed-out messages: clientId - {}, pack size - {}.",
                                consumerId, clientId, pack.messages().size()
                ));
            }
            if (pauseBetweenRetries > 0) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(pauseBetweenRetries));
                } catch (InterruptedException e) {
                    log.error("[{}] Failed to pause for retry.", consumerId);
                }
            }
            return new DeviceProcessingDecision(false, toReprocess);
        }
    }

}
