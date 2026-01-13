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
package org.thingsboard.mqtt.broker.integration.api;

import lombok.ToString;

import java.util.concurrent.atomic.AtomicLong;

@ToString
public class IntegrationStatistics {

    private final IntegrationContext ctx;
    private final AtomicLong messagesProcessed;
    private final AtomicLong errorsOccurred;

    public IntegrationStatistics(IntegrationContext ctx) {
        this.ctx = ctx;
        this.messagesProcessed = new AtomicLong(0);
        this.errorsOccurred = new AtomicLong(0);
    }

    public void incMessagesProcessed() {
        messagesProcessed.incrementAndGet();
        ctx.onUplinkMessageProcessed(true);
    }

    public void incErrorsOccurred() {
        errorsOccurred.incrementAndGet();
        ctx.onUplinkMessageProcessed(false);
    }

    public long getMessagesProcessed() {
        return messagesProcessed.get();
    }

    public long getErrorsOccurred() {
        return errorsOccurred.get();
    }

    public boolean isEmpty() {
        return getMessagesProcessed() == 0 && getErrorsOccurred() == 0;
    }

}
