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
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationMsgProto;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationNotificationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.TbmqIntegrationExecutorComponent;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.TBMQ_IE_NOT_IMPLEMENTED;

@Component
@Slf4j
@RequiredArgsConstructor
@TbmqIntegrationExecutorComponent
public class ExecutorIntegrationUplinkQueueProvider implements IntegrationUplinkQueueProvider {

    private final IntegrationUplinkQueueFactory integrationUplinkQueueFactory;
    private final IntegrationUplinkNotificationsQueueFactory uplinkNotificationsQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    private TbQueueProducer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> integrationUplinkProducer;
    private TbQueueProducer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> uplinkNotificationsProducer;

    @PostConstruct
    public void init() {
        var serviceId = serviceInfoProvider.getServiceId();

        this.integrationUplinkProducer = integrationUplinkQueueFactory.createProducer(serviceId);
        this.uplinkNotificationsProducer = uplinkNotificationsQueueFactory.createProducer(serviceId);
    }

    @PreDestroy
    public void destroy() {
        if (integrationUplinkProducer != null) {
            integrationUplinkProducer.stop();
        }
        if (uplinkNotificationsProducer != null) {
            uplinkNotificationsProducer.stop();
        }
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> getIeUplinkProducer() {
        return integrationUplinkProducer;
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> getIeUplinkConsumer() {
        throw new RuntimeException(TBMQ_IE_NOT_IMPLEMENTED);
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> getIeUplinkNotificationsProducer() {
        return uplinkNotificationsProducer;
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> getIeUplinkNotificationsConsumer() {
        throw new RuntimeException(TBMQ_IE_NOT_IMPLEMENTED);
    }

}
