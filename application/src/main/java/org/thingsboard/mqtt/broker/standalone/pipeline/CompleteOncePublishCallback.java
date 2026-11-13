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
package org.thingsboard.mqtt.broker.standalone.pipeline;

import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.processing.PublishMsgCallback;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The ack point of a publish: it completes once every copy was handed to Netty or written to the store.
 * <p>
 * The first {@link #onSuccess()} or {@link #onFailure(Throwable)} gives the publish's share back to the budget and
 * completes the publisher's callback; every later call is ignored. That makes one failing copy among many give exactly
 * one negative acknowledgement, although {@code MultiplePublishMsgCallbackWrapper} reports a failure once per copy.
 * The publisher's callback completes even when the release throws: a publish that is never answered would hold every
 * later acknowledgement of its client back for good.
 */
public class CompleteOncePublishCallback implements PublishMsgCallback {

    private final PipelineBudget budget;
    private final int bytes;
    private final TbQueueCallback callback;
    private final AtomicBoolean completed = new AtomicBoolean();

    public CompleteOncePublishCallback(PipelineBudget budget, int bytes, TbQueueCallback callback) {
        this.budget = budget;
        this.bytes = bytes;
        this.callback = callback;
    }

    @Override
    public void onSuccess() {
        if (completed.compareAndSet(false, true)) {
            try {
                budget.release(bytes);
            } finally {
                callback.onSuccess(null);
            }
        }
    }

    @Override
    public void onFailure(Throwable t) {
        if (completed.compareAndSet(false, true)) {
            try {
                budget.release(bytes);
            } finally {
                callback.onFailure(t);
            }
        }
    }

}
