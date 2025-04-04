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
package org.thingsboard.mqtt.broker.queue.common;


import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;

import java.util.HashMap;
import java.util.Map;

public class DefaultTbQueueMsgHeaders implements TbQueueMsgHeaders {

    private final Map<String, byte[]> data;

    public DefaultTbQueueMsgHeaders() {
        this.data = new HashMap<>();
    }

    public DefaultTbQueueMsgHeaders(Map<String, byte[]> data) {
        this.data = data;
    }

    @Override
    public byte[] put(String key, byte[] value) {
        return data.put(key, value);
    }

    @Override
    public byte[] get(String key) {
        return data.get(key);
    }

    @Override
    public Map<String, byte[]> getData() {
        return data;
    }

    @Override
    public DefaultTbQueueMsgHeaders copy() {
        return new DefaultTbQueueMsgHeaders(this.data);
    }
}
