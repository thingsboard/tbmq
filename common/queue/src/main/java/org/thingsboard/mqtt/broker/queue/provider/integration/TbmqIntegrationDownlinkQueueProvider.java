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
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.common.data.integration.IntegrationType;
import org.thingsboard.mqtt.broker.exception.ThingsboardRuntimeException;
import org.thingsboard.mqtt.broker.gen.integration.DownlinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.TbmqComponent;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

import java.util.Map;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.TBMQ_NOT_IMPLEMENTED;

@Component
@Slf4j
@RequiredArgsConstructor
@TbmqComponent
public class TbmqIntegrationDownlinkQueueProvider implements IntegrationDownlinkQueueProvider {

    private final HttpIntegrationDownlinkQueueFactory httpIntegrationDownlinkQueueFactory;
    private final KafkaIntegrationDownlinkQueueFactory kafkaIntegrationDownlinkQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    private TbQueueProducer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> httpIntegrationDownlinkProducer;
    private TbQueueProducer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> kafkaIntegrationDownlinkProducer;

    @PostConstruct
    public void init() {
        this.httpIntegrationDownlinkProducer = httpIntegrationDownlinkQueueFactory.createProducer(serviceInfoProvider.getServiceId());
        this.kafkaIntegrationDownlinkProducer = kafkaIntegrationDownlinkQueueFactory.createProducer(serviceInfoProvider.getServiceId());
    }

    @PreDestroy
    public void destroy() {
        if (httpIntegrationDownlinkProducer != null) {
            httpIntegrationDownlinkProducer.stop();
        }
        if (kafkaIntegrationDownlinkProducer != null) {
            kafkaIntegrationDownlinkProducer.stop();
        }
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>> getIeDownlinkProducer(IntegrationType type) {
        return switch (type) {
            case HTTP -> httpIntegrationDownlinkProducer;
            case KAFKA -> kafkaIntegrationDownlinkProducer;
//            case MQTT -> throw new ThingsboardRuntimeException("MQTT integration type is not yet implemented!");
            default -> throw new ThingsboardRuntimeException("Unsupported integration type: " + type);
        };
    }

    @Override
    public Map<IntegrationType, TbQueueControlledOffsetConsumer<TbProtoQueueMsg<DownlinkIntegrationMsgProto>>> getIeDownlinkConsumers() {
        throw new RuntimeException(TBMQ_NOT_IMPLEMENTED);
    }
}
