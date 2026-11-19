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

/**
 * What a write does to the amount of data in the store. A store that is running out of room refuses
 * {@link #NEW_DATA} first and keeps accepting the writes that do not grow it. This is the one place that states how a
 * write is classed:
 * <ul>
 *   <li>A batch is classed by its net effect: a batch that adds entries is {@link #NEW_DATA}, one that only replaces or
 *   moves entries is {@link #UPDATE}, and one that frees room is {@link #DELETE}, even when it also rewrites the
 *   metadata of the queue it removes from.</li>
 *   <li>A management write (an entity save, a blocked client) and a retained message are {@link #NEW_DATA} even when
 *   they replace an entry: a full disk refuses management creates and updates alike, and a retained message adds a
 *   payload.</li>
 *   <li>A session attribute is {@link #UPDATE} even when its key is new: it is small, it is removed with its session,
 *   and it is written on every connect, which a full disk must not refuse for a non-persistent client.</li>
 * </ul>
 */
public enum StoreWriteClass {

    /**
     * Adds an entry that did not exist, or a message that must be kept.
     */
    NEW_DATA,

    /**
     * Replaces the value of an entry that exists, or moves existing data.
     */
    UPDATE,

    /**
     * Removes entries. Never refused, because it frees room.
     */
    DELETE

}
