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
package org.thingsboard.mqtt.broker.service.processing;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.queue.publish.TbPublishServiceImpl;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishMsgQueuePublisherImpl implements PublishMsgQueuePublisher {

    private final PublishMsgQueueFactory publishMsgQueueFactory;

    private TbPublishServiceImpl<PublishMsgProto> publisher;

    @PostConstruct
    public void init() {
        this.publisher = TbPublishServiceImpl.<PublishMsgProto>builder()
                .queueName("publishMsg")
                .producer(publishMsgQueueFactory.createProducer())
                .build();
        this.publisher.init();
    }

    @Override
    public void sendMsg(TbProtoQueueMsg<PublishMsgProto> msgProto, TbQueueCallback callback) {
        publisher.send(msgProto, callback);
    }

    @PreDestroy
    public void destroy() {
        publisher.destroy();
    }
}
