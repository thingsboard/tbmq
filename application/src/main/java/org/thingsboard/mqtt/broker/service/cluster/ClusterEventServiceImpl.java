/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.cluster;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.ClusterEventQueueFactory;
import org.thingsboard.mqtt.broker.service.cluster.model.ClusterEventType;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterEventServiceImpl implements ClusterEventService {

    private final ClusterEventQueueFactory clusterEventQueueFactory;
    private final ServiceInfoProvider serviceInfoProvider;

    private TbQueueProducer<TbProtoQueueMsg<QueueProtos.ClusterEventProto>> eventProducer;

    @PostConstruct
    public void init() {
        this.eventProducer = clusterEventQueueFactory.createEventProducer(serviceInfoProvider.getServiceId());
    }

    @Override
    public void sendApplicationQueueDeletedEvent(String clientId) {
        QueueProtos.ClusterEventProto eventProto = generateApplicationQueueDeletedEvent(clientId);

        log.trace("[{}][{}] Sending cluster event request.", clientId, eventProto.getEventType());
        eventProducer.send(new TbProtoQueueMsg<>(serviceInfoProvider.getServiceId(), eventProto), new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("[{}][{}] Request sent: {}", clientId, eventProto.getEventType(), metadata);
            }

            @Override
            public void onFailure(Throwable t) {
                log.debug("[{}][{}] Failed to send request. Exception - {}, reason - {}.", clientId, eventProto.getEventType(), t.getClass().getSimpleName(), t.getMessage());
            }
        });
    }

    private QueueProtos.ClusterEventProto generateApplicationQueueDeletedEvent(String clientId) {
        return QueueProtos.ClusterEventProto.newBuilder()
                .setServiceInfo(serviceInfoProvider.getServiceInfo())
                .setEventType(ClusterEventType.APPLICATION_QUEUE_DELETED_EVENT.toString())
                .setApplicationQueueRemovedEventProto(QueueProtos.ApplicationQueueRemovedEventProto.newBuilder()
                        .setClientId(clientId)
                        .build())
                .build();
    }

    @PreDestroy
    public void destroy() {
        if (eventProducer != null) {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            eventProducer.stop();

            stopWatch.stop();
            log.info("Cluster Event producer stopped for {} ms.", stopWatch.getTime());
        }
    }
}
