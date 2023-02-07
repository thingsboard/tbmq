/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.session;

import java.util.concurrent.atomic.AtomicInteger;

public class MsgIdSequence {
    private final AtomicInteger msgIdSeq = new AtomicInteger(1);

    public int nextMsgId() {
        synchronized (this.msgIdSeq) {
            this.msgIdSeq.compareAndSet(0xffff, 1);
            return this.msgIdSeq.getAndIncrement();
        }
    }

    public void updateMsgIdSequence(int lastMsgId) {
        synchronized (this.msgIdSeq) {
            if (lastMsgId >= 0xffff) {
                this.msgIdSeq.set(1);
            } else {
                this.msgIdSeq.set(lastMsgId + 1);
            }
        }
    }
}
