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
import org.thingsboard.mqtt.broker.queue.TbQueueControlledOffsetConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.TbmqComponent;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.integration.IntegrationUplinkNotificationsHelper;

import static org.thingsboard.mqtt.broker.queue.constants.QueueConstants.TBMQ_NOT_IMPLEMENTED;

@Component
@Slf4j
@RequiredArgsConstructor
@TbmqComponent
public class TbmqIntegrationUplinkQueueProvider implements IntegrationUplinkQueueProvider {

    private final IntegrationUplinkQueueFactory integrationUplinkQueueFactory;
    private final IntegrationUplinkNotificationsQueueFactory uplinkNotificationsQueueFactory;
    private final IntegrationUplinkNotificationsHelper uplinkNotificationsHelper;
    private final ServiceInfoProvider serviceInfoProvider;

    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> integrationUplinkConsumer;
    private TbQueueControlledOffsetConsumer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> uplinkNotificationsConsumer;

    @PostConstruct
    public void init() {
        var serviceId = serviceInfoProvider.getServiceId();

        this.integrationUplinkConsumer = integrationUplinkQueueFactory.createConsumer(serviceId);
        this.uplinkNotificationsConsumer = uplinkNotificationsQueueFactory.createConsumer(uplinkNotificationsHelper.getServiceTopic(serviceId), serviceId);
    }

    @PreDestroy
    public void destroy() {
        if (integrationUplinkConsumer != null) {
            integrationUplinkConsumer.unsubscribeAndClose();
        }
        if (uplinkNotificationsConsumer != null) {
            uplinkNotificationsConsumer.unsubscribeAndClose();
        }
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> getIeUplinkProducer() {
        throw new RuntimeException(TBMQ_NOT_IMPLEMENTED);
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<UplinkIntegrationMsgProto>> getIeUplinkConsumer() {
        return integrationUplinkConsumer;
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> getIeUplinkNotificationsProducer() {
        throw new RuntimeException(TBMQ_NOT_IMPLEMENTED);
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> getIeUplinkNotificationsConsumer() {
        return uplinkNotificationsConsumer;
    }

}
