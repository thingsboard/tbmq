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
package org.thingsboard.mqtt.broker.service.processing;

import java.util.concurrent.atomic.AtomicInteger;

public class MultiplePublishMsgCallbackWrapper implements PublishMsgCallback {
    private final AtomicInteger callbackCount;
    private final PublishMsgCallback callback;

    public MultiplePublishMsgCallbackWrapper(int callbackCount, PublishMsgCallback callback) {
        this.callbackCount = new AtomicInteger(callbackCount);
        this.callback = callback;
    }

    @Override
    public void onSuccess() {
        if (callbackCount.decrementAndGet() <= 0) {
            callback.onSuccess();
        }
    }

    @Override
    public void onFailure(Throwable t) {
        callback.onFailure(t);
    }
}
