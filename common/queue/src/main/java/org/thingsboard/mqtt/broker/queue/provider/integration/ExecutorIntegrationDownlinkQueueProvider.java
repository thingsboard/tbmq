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
package org.thingsboard.mqtt.broker.queue.provider.integration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.exception.ThingsboardRuntimeException;
import org.thingsboard.mqtt.broker.gen.integration.DownlinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.TbmqIntegrationExecutorComponent;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

import java.util.HashMap;
import java.util.Map;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.TBMQ_IE_NOT_IMPLEMENTED;

@Component
@Slf4j
@RequiredArgsConstructor
@TbmqIntegrationExecutorComponent
public class ExecutorIntegrationDownlinkQueueProvider implements IntegrationDownlinkQueueProvider {

    private final ServiceInfoProvider serviceInfoProvider;
    private final @Lazy HttpIntegrationDownlinkQueueFactory httpIntegrationDownlinkQueueFactory;
    private final @Lazy KafkaIntegrationDownlinkQueueFactory kafkaIntegrationDownlinkQueueFactory;

    private Map<IntegrationType, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>>> consumers;

    @PostConstruct
    public void init() {
        var supportedIntegrationTypes = serviceInfoProvider.getSupportedIntegrationTypes();
        log.info("Initializing ExecutorIntegrationDownlinkQueueProvider: {}", supportedIntegrationTypes);
        if (CollectionUtils.isEmpty(supportedIntegrationTypes)) {
            return;
        }
        consumers = new HashMap<>();

        String serviceId = serviceInfoProvider.getServiceId();
        for (IntegrationType integrationType : supportedIntegrationTypes) {
            consumers.put(integrationType, getConsumer(integrationType, serviceId));
        }
    }

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> getConsumer(IntegrationType integrationType, String serviceId) {
        return switch (integrationType) {
            case HTTP -> httpIntegrationDownlinkQueueFactory.createConsumer(serviceId);
            case KAFKA -> kafkaIntegrationDownlinkQueueFactory.createConsumer(serviceId);
//            case MQTT -> throw new ThingsboardRuntimeException("MQTT integration type is not yet implemented!");
            default -> throw new ThingsboardRuntimeException("Unsupported integration type: " + integrationType);
        };
    }

    @PreDestroy
    public void destroy() {
        // No need to destroy consumers here!
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> getIeDownlinkProducer(IntegrationType type) {
        throw new RuntimeException(TBMQ_IE_NOT_IMPLEMENTED);
    }

    @Override
    public Map<IntegrationType, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>>> getIeDownlinkConsumers() {
        return consumers == null ? Map.of() : consumers;
    }
}
