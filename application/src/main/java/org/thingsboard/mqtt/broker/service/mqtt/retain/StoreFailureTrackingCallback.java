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
package org.thingsboard.mqtt.broker.service.mqtt.retain;

import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

/**
 * Passes the outcome of a retained-message write on, and remembers a {@link StoreException}. The standalone store
 * writes before the persist call returns, so its failure is known right after that call. The cluster producer reports
 * later and never with a {@link StoreException}, so nothing is remembered there.
 */
final class StoreFailureTrackingCallback implements BasicCallback {

    private final BasicCallback delegate;
    private volatile StoreException storeFailure;

    StoreFailureTrackingCallback(BasicCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onSuccess() {
        delegate.onSuccess();
    }

    @Override
    public void onFailure(Throwable t) {
        if (t instanceof StoreException e) {
            storeFailure = e;
        }
        delegate.onFailure(t);
    }

    boolean storeFailed() {
        return storeFailure != null;
    }

    void throwIfStoreFailed() {
        StoreException failure = storeFailure;
        if (failure != null) {
            throw failure;
        }
    }

}
