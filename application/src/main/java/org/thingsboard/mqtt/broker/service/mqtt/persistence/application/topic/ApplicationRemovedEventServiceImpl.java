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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.ApplicationRemovedEventProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ApplicationRemovedEventQueueFactory;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationRemovedEventServiceImpl implements ApplicationRemovedEventService {

    private final ApplicationRemovedEventQueueFactory applicationRemovedEventQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    private TbQueueProducer<TbProtoQueueMsg<ApplicationRemovedEventProto>> eventProducer;

    @PostConstruct
    public void init() {
        eventProducer = applicationRemovedEventQueueFactory.createEventProducer(serviceInfoProvider.getServiceId());
    }

    @Override
    public void sendApplicationRemovedEvent(String clientId) {
        log.trace("[{}] Sending application removed event.", clientId);

        var eventProto = ApplicationRemovedEventProto.newBuilder().setClientId(clientId).build();
        eventProducer.send(new TbProtoQueueMsg<>(serviceInfoProvider.getServiceId(), eventProto), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("[{}] Event sent: {}", clientId, metadata);
            }

            @Override
            public void onFailure(Throwable t) {
                log.debug("[{}] Failed to send event", clientId, t);
            }
        });
    }

    @PreDestroy
    public void destroy() {
        if (eventProducer != null) {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            eventProducer.stop();

            stopWatch.stop();
            log.info("App Removed Event producer stopped within {} ms.", stopWatch.getTime());
        }
    }
}
