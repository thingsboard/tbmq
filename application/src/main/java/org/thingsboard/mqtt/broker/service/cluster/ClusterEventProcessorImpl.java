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
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.service.cluster.model.ApplicationQueueDeletedEvent;
import org.thingsboard.mqtt.broker.service.cluster.model.ClusterEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterEventProcessorImpl implements ClusterEventProcessor {

    @Override
    public void processClusterEvent(ClusterEvent event) {
        switch (event.getType()) {
            case APPLICATION_QUEUE_DELETED_EVENT:
                processApplicationQueueDeleted((ApplicationQueueDeletedEvent) event);
                return;
            default:
                throw new RuntimeException("Unexpected event type " + event.getType());
        }
    }

    private void processApplicationQueueDeleted(ApplicationQueueDeletedEvent event) {
    }
}
