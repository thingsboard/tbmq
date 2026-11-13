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
package org.thingsboard.mqtt.broker.service.historical.stats;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.util.edition.ClusterComponent;
import org.thingsboard.mqtt.broker.config.HistoricalDataReportProperties;
import org.thingsboard.mqtt.broker.gen.queue.ToUsageStatsMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.HistoricalDataQueueFactory;

@Component
@ClusterComponent
@RequiredArgsConstructor
public class KafkaHistoricalStatsTotalPublisher implements HistoricalStatsTotalPublisher {

    private final HistoricalDataQueueFactory historicalDataQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;
    private final HistoricalStatsTotalHelper helper;
    private final HistoricalDataReportProperties historicalDataReportProperties;

    private TbQueueProducer<TbProtoQueueMsg<ToUsageStatsMsgProto>> historicalStatsProducer;

    @PostConstruct
    void init() {
        if (!historicalDataReportProperties.isEnabled()) {
            return;
        }
        historicalStatsProducer = historicalDataQueueFactory.createProducer(serviceInfoProvider.getServiceId());
    }

    @Override
    public void publish(ToUsageStatsMsgProto msg, TbQueueCallback callback) {
        historicalStatsProducer.send(helper.getTopic(), null, new TbProtoQueueMsg<>(msg), callback);
    }

    @PreDestroy
    void destroy() {
        if (historicalStatsProducer != null) {
            historicalStatsProducer.stop();
        }
    }

}
