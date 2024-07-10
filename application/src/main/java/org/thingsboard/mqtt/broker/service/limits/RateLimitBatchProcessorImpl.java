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
package org.thingsboard.mqtt.broker.service.limits;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
@Slf4j
@Data
@ConditionalOnProperty(prefix = "mqtt.rate-limits.total", value = "enabled", havingValue = "true")
public class RateLimitBatchProcessorImpl implements RateLimitBatchProcessor {

    private final RateLimitService rateLimitService;
    private final Queue<MessageWrapper> messageQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger messageCount = new AtomicInteger(0);

    @Value("${mqtt.rate-limits.threads-count:1}")
    private int rateLimitThreadsCount;
    @Value("${mqtt.rate-limits.batch-size:50}")
    private int batchSize;
    @Value("${mqtt.rate-limits.period-ms:50}")
    private long periodMs;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        scheduler = ThingsBoardExecutors.initScheduledExecutorService(rateLimitThreadsCount, "rate-limit-batch-processor");
        scheduler.scheduleAtFixedRate(this::processBatch, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public void addMessage(MqttPublishMsg message, Consumer<MqttPublishMsg> onSuccess, Consumer<MqttPublishMsg> onRateLimits) {
        messageQueue.add(new MessageWrapper(message, onSuccess, onRateLimits));
        int count = messageCount.incrementAndGet();
        if (count >= batchSize) {
            scheduler.execute(this::processBatch);
        }
    }

    void processBatch() {
        if (messageQueue.isEmpty()) {
            return;
        }

        int tokensToConsume = messageCount.get();
        int availableTokens = (int) rateLimitService.tryConsumeAsMuchAsPossibleTotalMsgs(tokensToConsume);
        int messagesToProcess = Math.min(availableTokens, tokensToConsume);

        for (int i = 0; i < messagesToProcess; i++) {
            MessageWrapper message = poll();
            if (message != null) {
                processMessage(message);
            }
        }

        if (availableTokens < tokensToConsume) {
            while (!messageQueue.isEmpty()) {
                MessageWrapper message = poll();
                if (message != null) {
                    skipMessage(message);
                }
            }
        }
    }

    private MessageWrapper poll() {
        MessageWrapper message = messageQueue.poll();
        if (message != null) {
            messageCount.decrementAndGet();
        }
        return message;
    }

    private void processMessage(MessageWrapper message) {
        message.onSuccess.accept(message.getMqttPublishMsg());
    }

    private void skipMessage(MessageWrapper message) {
        message.onRateLimits.accept(message.getMqttPublishMsg());
    }

    @Data
    private static class MessageWrapper {
        private final MqttPublishMsg mqttPublishMsg;
        private final Consumer<MqttPublishMsg> onSuccess;
        private final Consumer<MqttPublishMsg> onRateLimits;
    }
}
