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
package org.thingsboard.mqtt.broker.queue.integration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.integration.IntegrationValidationResponseProto;
import org.thingsboard.mqtt.broker.gen.integration.UplinkIntegrationNotificationMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.TbmqIntegrationExecutorComponent;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.integration.IntegrationUplinkQueueProvider;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@TbmqIntegrationExecutorComponent
public class IntegrationUplinkNotificationsServiceImpl implements IntegrationUplinkNotificationsService {

    private final IntegrationUplinkQueueProvider uplinkQueueProvider;
    private final IntegrationUplinkNotificationsHelper helper;

    private TbQueueProducer<TbProtoQueueMsg<UplinkIntegrationNotificationMsgProto>> uplinkNotificationsProducer;

    @PostConstruct
    public void init() {
        this.uplinkNotificationsProducer = uplinkQueueProvider.getIeUplinkNotificationsProducer();
    }

    @Override
    public void sendNotification(IntegrationValidationResponseProto response, String serviceId, UUID requestId) {
        UplinkIntegrationNotificationMsgProto msg = UplinkIntegrationNotificationMsgProto.newBuilder().setIntegrationValidationResponseMsg(response).build();

        String topic = helper.getServiceTopic(serviceId);
        uplinkNotificationsProducer.send(topic, null, new TbProtoQueueMsg<>(UUID.randomUUID(), msg), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                if (log.isTraceEnabled()) {
                    log.trace("[{}] Published the validation response", requestId);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] Failed to publish the validation response", requestId, t);
            }
        });
    }

}
