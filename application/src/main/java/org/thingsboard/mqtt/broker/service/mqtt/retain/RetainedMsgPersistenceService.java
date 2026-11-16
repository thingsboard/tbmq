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
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.RetainedMsgProto;

/**
 * Writes the retained msg of a topic, or clears it with {@code QueueConstants.EMPTY_RETAINED_MSG_PROTO}.
 * <p>
 * The two async methods report the outcome to the callback in one of two ways, and callers rely on that:
 * <ul>
 *     <li>the write is done, and the callback completed, before the method returns; a failed write is reported as a
 *     {@link org.thingsboard.mqtt.broker.dao.store.StoreException} (the standalone store);</li>
 *     <li>the outcome may be reported later, and never with a
 *     {@link org.thingsboard.mqtt.broker.dao.store.StoreException} (the cluster producer).</li>
 * </ul>
 * So a {@link org.thingsboard.mqtt.broker.dao.store.StoreException} reported before the method returned means the
 * write did not happen: the caller leaves the retained msg of the topic as it was, and can still fail the request
 * that asked for the write.
 */
public interface RetainedMsgPersistenceService {

    void persistRetainedMsgAsync(String topic, RetainedMsgProto retainedMsgProto, BasicCallback callback);

    void persistRetainedMsgAsync(String topic, RetainedMsgProto retainedMsgProto, BasicCallback callback, int messageExpiryInterval);

    void persistRetainedMsgSync(String topic, RetainedMsgProto retainedMsgProto) throws QueuePersistenceException;

}
