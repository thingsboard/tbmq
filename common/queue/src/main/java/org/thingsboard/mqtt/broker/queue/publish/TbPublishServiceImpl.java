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
package org.thingsboard.mqtt.broker.queue.publish;

import com.google.protobuf.GeneratedMessageV3;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

@Slf4j
public class TbPublishServiceImpl<PROTO extends GeneratedMessageV3> implements TbPublishService<PROTO> {

    private final TbQueueProducer<TbProtoQueueMsg<PROTO>> producer;
    private final String queueName;
    private final Integer partition;

    @Builder
    public TbPublishServiceImpl(TbQueueProducer<TbProtoQueueMsg<PROTO>> producer, String queueName,
                                Integer partition) {
        this.producer = producer;
        this.queueName = queueName;
        this.partition = partition;
    }

    @Override
    public void init() {
    }

    @Override
    public void send(TbProtoQueueMsg<PROTO> queueMsg, TbQueueCallback callback) {
        send(queueMsg, callback, null);
    }

    @Override
    public void send(TbProtoQueueMsg<PROTO> queueMsg, TbQueueCallback callback, String topic) {
        try {
            if (topic != null) {
                producer.send(topic, partition, queueMsg, callback);
            } else {
                producer.send(queueMsg, callback);
            }
        } catch (Exception e) {
            log.error("[{}] Failed to send msg to the queue", queueName, e);
            callback.onFailure(e);
        }
    }

    @Override
    public void destroy() {
    }

}
