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
package org.thingsboard.mqtt.broker.queue.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.thingsboard.mqtt.broker.queue.TbQueueMsg;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.DefaultTbQueueMsgHeaders;

import java.util.UUID;

public class KafkaTbQueueMsg implements TbQueueMsg {
    private final String key;
    private final TbQueueMsgHeaders headers;
    private final byte[] data;

    public KafkaTbQueueMsg(ConsumerRecord<String, byte[]> record) {
        this.key = record.key();
        TbQueueMsgHeaders headers = new DefaultTbQueueMsgHeaders();
        record.headers().forEach(header -> {
            headers.put(header.key(), header.value());
        });
        this.headers = headers;
        this.data = record.value();
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public TbQueueMsgHeaders getHeaders() {
        return headers;
    }

    @Override
    public byte[] getData() {
        return data;
    }
}
