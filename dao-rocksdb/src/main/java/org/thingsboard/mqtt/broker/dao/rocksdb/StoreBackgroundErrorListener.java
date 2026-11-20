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
package org.thingsboard.mqtt.broker.dao.rocksdb;

import lombok.extern.slf4j.Slf4j;
import org.rocksdb.AbstractEventListener;
import org.rocksdb.BackgroundErrorReason;
import org.rocksdb.Status;

import java.util.function.Consumer;

/**
 * Passes RocksDB background errors (a failed flush, compaction or WAL write, which make the database refuse writes)
 * to {@code reporter} as {@code "<reason>: <code>(<subcode>) <message>"}. Only reporting: RocksJava 9.10.0 has no
 * {@code DB::Resume()} and its {@code onErrorRecoveryCompleted} fires for failed recoveries too, so the store learns that
 * it recovered with a probe write ({@link RocksDbStore#resumeAfterBackgroundError()}).
 */
@Slf4j
class StoreBackgroundErrorListener extends AbstractEventListener {

    private final Consumer<String> reporter;

    StoreBackgroundErrorListener(Consumer<String> reporter) {
        super(EnabledEventCallback.ON_BACKGROUND_ERROR);
        this.reporter = reporter;
    }

    @Override
    public void onBackgroundError(BackgroundErrorReason reason, Status backgroundError) {
        String state = backgroundError.getState();
        try {
            reporter.accept(reason + ": " + backgroundError.getCodeString()
                    + (state == null || state.isEmpty() ? "" : " " + state));
        } catch (RuntimeException e) {
            // runs on a RocksDB thread, an exception must not reach the native code
            log.error("Failed to report a background error of the store", e);
        }
    }

}
