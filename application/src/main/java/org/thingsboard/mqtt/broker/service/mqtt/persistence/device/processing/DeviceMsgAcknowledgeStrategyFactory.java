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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.device.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceMsgAcknowledgeStrategyFactory {
    private final DeviceAckStrategyConfiguration ackStrategyConfiguration;

    public DeviceAckStrategy newInstance(String consumerId) {
        switch (ackStrategyConfiguration.getType()) {
            case SKIP_ALL:
                return new SkipStrategy(consumerId);
            case RETRY_ALL:
                return new RetryStrategy(consumerId, ackStrategyConfiguration);
            default:
                throw new RuntimeException("DeviceAckStrategy with type " + ackStrategyConfiguration.getType() + " is not supported!");
        }
    }

    @RequiredArgsConstructor
    private static class SkipStrategy implements DeviceAckStrategy {
        private final String consumerId;

        @Override
        public DeviceProcessingDecision analyze(DevicePackProcessingContext processingContext) {
            if (!processingContext.isSuccessful()) {
                log.debug("[{}] Skip reprocess for {} messages.", consumerId, processingContext.getMessages().size());
            }
            if (log.isTraceEnabled()) {
                processingContext.getMessages().forEach((msg) ->
                        log.trace("[{}] Failed message: clientId - {}, topic - {}.",
                                consumerId, msg.getClientId(), msg.getTopic())
                );
            }
            return new DeviceProcessingDecision(true);
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
        public DeviceProcessingDecision analyze(DevicePackProcessingContext processingContext) {
            if (processingContext.isSuccessful()) {
                return new DeviceProcessingDecision(true);
            }
            if (maxRetries != 0 && ++retryCount > maxRetries) {
                log.debug("[{}] Skip reprocess due to max retries.", consumerId);
                return new DeviceProcessingDecision(true);
            }
            log.debug("[{}] Going to reprocess {} messages", consumerId, processingContext.getMessages().size());
            if (log.isTraceEnabled()) {
                processingContext.getMessages().forEach((msg) ->
                        log.trace("[{}] Going to reprocess message: clientId - {}, topic - {}.",
                                consumerId, msg.getClientId(), msg.getTopic())
                );
            }
            if (pauseBetweenRetries > 0) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(pauseBetweenRetries));
                } catch (InterruptedException e) {
                    log.error("[{}] Failed to pause for retry.", consumerId);
                }
            }
            return new DeviceProcessingDecision(false);
        }
    }
}
