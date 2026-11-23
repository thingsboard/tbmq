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
package org.thingsboard.mqtt.broker.dao.store;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes a consistent point-in-time backup of the embedded broker store as a gzip-compressed tar archive. One backup
 * runs at a time: while one runs, another is refused with {@link BackupInProgressException}.
 */
public interface StoreBackup {

    /**
     * Takes the single backup slot without writing anything yet, so that a caller can refuse a second backup before it
     * commits to a response, and write the archive later, on another thread.
     *
     * @throws BackupInProgressException while another backup holds the slot
     * @throws StoreException            when the store cannot be backed up at all, e.g. its backups directory is not set
     */
    Reservation reserve();

    /**
     * Writes a backup archive to {@code out}, which stays open: {@link #reserve()}, then {@link Reservation#writeTo}.
     *
     * @throws BackupInProgressException while another backup runs
     * @throws IOException               when {@code out} fails, e.g. the client is gone; the archive written so far is
     *                                   left incomplete, so that it never reads as a whole backup
     */
    default void write(OutputStream out) throws IOException {
        try (Reservation reservation = reserve()) {
            reservation.writeTo(out);
        }
    }

    /**
     * The backup slot taken by {@link #reserve()}. {@link #writeTo} may be called once, from any thread, and releases
     * the slot when it ends. {@link #close()} releases a slot that was never written with, and does nothing while
     * {@link #writeTo} runs or after it, so it is safe to call from every path that ends the request.
     */
    interface Reservation extends AutoCloseable {

        /**
         * Writes the archive to {@code out}, which stays open, and releases the slot.
         *
         * @throws IllegalStateException when the reservation was already written with or closed
         */
        void writeTo(OutputStream out) throws IOException;

        @Override
        void close();

    }

}
