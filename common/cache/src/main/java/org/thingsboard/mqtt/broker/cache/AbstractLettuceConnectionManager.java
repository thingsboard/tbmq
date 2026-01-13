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
package org.thingsboard.mqtt.broker.cache;

import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractLettuceConnectionManager {

    @Value("${lettuce.auto-flush:true}")
    protected boolean autoFlush;

    @Value("${lettuce.buffered-cmd-count:5}")
    protected int maxBufferedCmdCount;

    protected final AtomicInteger bufferedCmdCount = new AtomicInteger(0);

    protected void doFlushOnTimeThreshold() {
        if (!autoFlush && bufferedCmdCount.get() > 0) {
            flushCommands();
        }
    }

    protected void forceFlush() {
        if (!autoFlush) {
            flushCommands();
        }
    }

    protected void flushIfNeeded() {
        if (!autoFlush && bufferedCmdCount.incrementAndGet() >= maxBufferedCmdCount) {
            flushCommands();
        }
    }

    protected abstract void flushCommands();

}
