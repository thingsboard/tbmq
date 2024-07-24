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

import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttPublishMsg;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RateLimitBatchProcessorImplTest {

    private RateLimitService rateLimitService;
    private RateLimitBatchProcessorImpl rateLimitBatchProcessor;

    @Before
    public void setUp() {
        rateLimitService = mock(RateLimitService.class);
        rateLimitBatchProcessor = spy(new RateLimitBatchProcessorImpl(rateLimitService));

        rateLimitBatchProcessor.setRateLimitThreadsCount(1);
        rateLimitBatchProcessor.setBatchSize(50);
        rateLimitBatchProcessor.setPeriodMs(60000L);
        rateLimitBatchProcessor.init();
    }

    @Test
    public void addMessage_shouldProcessImmediatelyIfBatchSizeReached() {
        Consumer<MqttPublishMsg> onSuccess = mock(Consumer.class);
        Consumer<MqttPublishMsg> onRateLimits = mock(Consumer.class);
        MqttPublishMsg message = mock(MqttPublishMsg.class);

        for (int i = 0; i < 50; i++) {
            rateLimitBatchProcessor.addMessage(message, onSuccess, onRateLimits);
        }

        Awaitility.await().atMost(1, TimeUnit.SECONDS).until(() -> {
            rateLimitBatchProcessor.processBatch();
            return true;
        });

        verify(rateLimitBatchProcessor, atLeast(1)).processBatch();
    }

    @Test
    public void addMessage_shouldNotProcessImmediatelyIfBatchSizeIsNotReached() {
        Consumer<MqttPublishMsg> onSuccess = mock(Consumer.class);
        Consumer<MqttPublishMsg> onRateLimits = mock(Consumer.class);
        MqttPublishMsg message = mock(MqttPublishMsg.class);

        for (int i = 0; i < 10; i++) {
            rateLimitBatchProcessor.addMessage(message, onSuccess, onRateLimits);
        }

        verify(rateLimitBatchProcessor, never()).processBatch();
    }

    @Test
    public void processBatch_shouldConsumeAvailableTokens() {
        Consumer<MqttPublishMsg> onSuccess = mock(Consumer.class);
        Consumer<MqttPublishMsg> onRateLimits = mock(Consumer.class);
        MqttPublishMsg message = mock(MqttPublishMsg.class);

        rateLimitBatchProcessor.addMessage(message, onSuccess, onRateLimits);

        when(rateLimitService.tryConsumeAsMuchAsPossibleTotalMsgs(1)).thenReturn(1L);

        rateLimitBatchProcessor.processBatch();

        verify(onSuccess, times(1)).accept(message);
        verify(onRateLimits, never()).accept(message);
    }

    @Test
    public void processBatch_shouldSkipMessagesIfTokensNotAvailable() {
        Consumer<MqttPublishMsg> onSuccess = mock(Consumer.class);
        Consumer<MqttPublishMsg> onRateLimits = mock(Consumer.class);
        MqttPublishMsg message = mock(MqttPublishMsg.class);

        rateLimitBatchProcessor.addMessage(message, onSuccess, onRateLimits);

        when(rateLimitService.tryConsumeAsMuchAsPossibleTotalMsgs(1)).thenReturn(0L);

        rateLimitBatchProcessor.processBatch();

        verify(onSuccess, never()).accept(message);
        verify(onRateLimits, times(1)).accept(message);
    }
}
