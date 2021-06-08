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

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.cluster.model.ApplicationQueueDeletedEvent;
import org.thingsboard.mqtt.broker.service.cluster.model.ClusterEvent;
import org.thingsboard.mqtt.broker.service.cluster.model.ClusterEventType;

import static org.thingsboard.mqtt.broker.service.cluster.model.ClusterEventType.APPLICATION_QUEUE_DELETED_EVENT;

@Service
public class ClusterEventConverterImpl implements ClusterEventConverter {
    @Override
    public ClusterEvent convert(QueueProtos.ClusterEventProto eventProto) {
        String serviceId = eventProto.getServiceInfo().getServiceId();
        if (StringUtils.isEmpty(serviceId)) {
            throw new RuntimeException("ServiceId is empty for Cluster event");
        }
        ClusterEventType eventType = ClusterEventType.valueOf(eventProto.getEventType());
        switch (eventType) {
            case APPLICATION_QUEUE_DELETED_EVENT:
                String clientId = eventProto.getApplicationQueueRemovedEventProto().getClientId();
                if (StringUtils.isEmpty(serviceId)) {
                    throw new RuntimeException("ClientId is empty for " + APPLICATION_QUEUE_DELETED_EVENT + " event");
                }
                return new ApplicationQueueDeletedEvent(serviceId, clientId);
            default:
                throw new RuntimeException("Unknown event type");
        }
    }
}
