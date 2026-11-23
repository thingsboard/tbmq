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
     * Takes the single backup slot and the point-in-time copy of the store the archive is made of, without writing
     * anything yet: a second backup, or a copy that cannot be taken, fails before the caller commits to a response, and
     * the archive is written later, possibly on another thread. The caller must write with or close the reservation,
     * which releases the slot and deletes the copy.
     *
     * @throws BackupInProgressException while another backup holds the slot
     * @throws StoreException            when the copy cannot be taken, e.g. the backups directory is not set or the
     *                                   disk is full
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
     * The backup slot and the copy of the store taken by {@link #reserve()}. {@link #writeTo} may be called once, from
     * any thread, and releases both when it ends. {@link #close()} releases a reservation that was never written with,
     * and does nothing while {@link #writeTo} runs or after it, so it is safe to call from every path that ends the
     * request.
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
