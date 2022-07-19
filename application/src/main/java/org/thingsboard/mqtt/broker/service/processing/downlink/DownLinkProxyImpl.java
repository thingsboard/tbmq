/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.processing.downlink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.processing.downlink.basic.BasicDownLinkProcessor;
import org.thingsboard.mqtt.broker.service.processing.downlink.persistent.PersistentDownLinkProcessor;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownLinkProxyImpl implements DownLinkProxy {
    private final ServiceInfoProvider serviceInfoProvider;
    private final DownLinkQueuePublisher queuePublisher;
    private final BasicDownLinkProcessor basicDownLinkProcessor;
    private final PersistentDownLinkProcessor persistentDownLinkProcessor;

    @Override
    public void sendBasicMsg(String targetServiceId, String clientId, QueueProtos.PublishMsgProto msg) {
        if (belongsToThisNode(targetServiceId)) {
            basicDownLinkProcessor.process(clientId, msg);
        } else {
            queuePublisher.publishBasicMsg(targetServiceId, clientId, msg);
        }
    }

    @Override
    public void sendPersistentMsg(String targetServiceId, String clientId, QueueProtos.DevicePublishMsgProto msg) {
        if (belongsToThisNode(targetServiceId)) {
            persistentDownLinkProcessor.process(clientId, msg);
        } else {
            queuePublisher.publishPersistentMsg(targetServiceId, clientId, msg);
        }
    }

    private boolean belongsToThisNode(String targetServiceId) {
        return targetServiceId.equals(serviceInfoProvider.getServiceId());
    }
}
