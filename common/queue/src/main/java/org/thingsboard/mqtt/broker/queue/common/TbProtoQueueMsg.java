/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.queue.common;

import lombok.Data;
import org.thingsboard.mqtt.broker.queue.TbQueueMsg;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;

@Data
public class TbProtoQueueMsg<T extends com.google.protobuf.GeneratedMessageV3> implements TbQueueMsg {

    private final String key;
    protected final T value;
    private final TbQueueMsgHeaders headers;
    private final int partition;
    private final long offset;

    public TbProtoQueueMsg(T value) {
        this(null, value, new DefaultTbQueueMsgHeaders());
    }

    public TbProtoQueueMsg(T value, TbQueueMsgHeaders headers) {
        this(null, value, headers);
    }

    public TbProtoQueueMsg(String key, T value) {
        this(key, value, new DefaultTbQueueMsgHeaders());
    }

    public TbProtoQueueMsg(String key, T value, TbQueueMsgHeaders headers) {
        this(key, value, headers, -1, -1);
    }

    public TbProtoQueueMsg(String key, T value, TbQueueMsgHeaders headers, int partition, long offset) {
        this.key = key;
        this.value = value;
        this.headers = headers;
        this.partition = partition;
        this.offset = offset;
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
        return value.toByteArray();
    }

    @Override
    public int getPartition() {
        return partition;
    }

    @Override
    public long getOffset() {
        return offset;
    }
}
