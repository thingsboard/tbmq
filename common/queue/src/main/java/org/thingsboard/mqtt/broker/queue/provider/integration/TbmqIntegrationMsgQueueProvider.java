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
package org.thingsboard.mqtt.broker.queue.provider.integration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.gen.integration.PublishIntegrationMsgProto;
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
public class TbmqIntegrationMsgQueueProvider implements IntegrationMsgQueueProvider {

    private final IntegrationMsgQueueFactory integrationMsgQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    private TbQueueProducer<TbProtoQueueMsg<PublishIntegrationMsgProto>> integrationMsgProducer;

    @PostConstruct
    public void init() {
        this.integrationMsgProducer = integrationMsgQueueFactory.createProducer(serviceInfoProvider.getServiceId());
    }

    @PreDestroy
    public void destroy() {
        if (integrationMsgProducer != null) {
            integrationMsgProducer.stop();
        }
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<PublishIntegrationMsgProto>> getIeMsgProducer() {
        return integrationMsgProducer;
    }

    @Override
    public TbQueueControlledOffsetConsumer<TbProtoQueueMsg<PublishIntegrationMsgProto>> getNewIeMsgConsumer(String topic, String consumerGroupId, String integrationId) {
        throw new RuntimeException(TBMQ_NOT_IMPLEMENTED);
    }

    @Override
    public Map<String, String> getTopicConfigs() {
        throw new RuntimeException(TBMQ_NOT_IMPLEMENTED);
    }
}
