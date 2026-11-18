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
 * {@link #NEW_DATA} first and keeps accepting the writes that do not grow it. A batch is classed by its net effect: a
 * batch that adds entries is {@link #NEW_DATA}, one that only replaces or moves entries is {@link #UPDATE}, and one that
 * frees room is {@link #DELETE}, even when it also rewrites the metadata of the queue it removes from.
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
