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
package org.thingsboard.mqtt.broker.integration.service.api;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.common.stats.StubMessagesStats;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardExecutors;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationEventProto;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.integration.api.IntegrationCallback;
import org.thingsboard.mqtt.broker.integration.api.IntegrationStatisticsService;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationUplinkQueueProvider;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
@Service
@Slf4j
public class DefaultIntegrationApiService implements IntegrationApiService {

    private final Optional<IntegrationStatisticsService> integrationStatisticsService;
    private final IntegrationUplinkQueueProvider queueProvider;

    private MessagesStats tbCoreProducerStats;
    private ExecutorService callbackExecutor;

    @Value("${integrations.uplink.callback-threads-count:4}")
    private int threadsCount;

    @PostConstruct
    public void init() {
        this.tbCoreProducerStats = integrationStatisticsService.isPresent() ? integrationStatisticsService.get().createIeUplinkPublishStats() : StubMessagesStats.STUB_MESSAGE_STATS;
        this.callbackExecutor = ThingsBoardExecutors.initExecutorService(threadsCount, "integration-uplink-callback");
    }

    @PreDestroy
    public void destroy() {
        if (callbackExecutor != null) {
            ThingsBoardExecutors.shutdownAndAwaitTermination(callbackExecutor, "IE uplink callback");
        }
    }

    @Override
    public void sendEventData(UUID entityId, IntegrationEventProto data, IntegrationCallback<Void> callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Pushing uplink integration event message {}", entityId, data);
        }
        tbCoreProducerStats.incrementTotal();
        StatsTbQueueCallback wrappedCallback = new StatsTbQueueCallback(
                callback != null ? new IntegrationTbQueueCallback(callbackExecutor, callback) : null,
                tbCoreProducerStats);

        var msg = UplinkIntegrationMsgProto.newBuilder().setEventProto(data).build();
        queueProvider.getIeUplinkProducer().send(new TbProtoQueueMsg<>(entityId, msg), wrappedCallback);
    }

}
