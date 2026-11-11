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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application;

import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;

@Service
@StandaloneComponent
public class NoOpApplicationMsgQueuePublisher implements ApplicationMsgQueuePublisher {

    private static final String NOT_SUPPORTED = "APPLICATION clients are not supported in TBMQ Lite";

    @Override
    public void sendMsg(String clientId, TbProtoQueueMsg<PublishMsgProto> queueMsg, PublishMsgCallback callback) {
        callback.onFailure(new UnsupportedOperationException(NOT_SUPPORTED));
    }

    @Override
    public void sendMsgToSharedTopic(String sharedTopic, TbProtoQueueMsg<PublishMsgProto> queueMsg, PublishMsgCallback callback) {
        callback.onFailure(new UnsupportedOperationException(NOT_SUPPORTED));
    }

}
