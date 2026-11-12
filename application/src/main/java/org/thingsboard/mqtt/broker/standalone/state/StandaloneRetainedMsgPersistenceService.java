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
package org.thingsboard.mqtt.broker.standalone.state;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.gen.queue.RetainedMsgProto;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgPersistenceService;

/**
 * Writes retained messages through to the embedded store. The write is done before the method returns, so the
 * callback (from which PUBACK is sent) fires only after the message is stored.
 */
@Slf4j
@Service
@StandaloneComponent
public class StandaloneRetainedMsgPersistenceService implements RetainedMsgPersistenceService {

    private final KeyValueStore store;

    public StandaloneRetainedMsgPersistenceService(BrokerStateStores stores) {
        this.store = stores.retainedMessages();
    }

    @Override
    public void persistRetainedMsgAsync(String topic, RetainedMsgProto retainedMsgProto, BasicCallback callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Persisting retained msg asynchronously - {}", topic, retainedMsgProto);
        }
        persist(topic, retainedMsgProto, null, callback);
    }

    @Override
    public void persistRetainedMsgAsync(String topic, RetainedMsgProto retainedMsgProto, BasicCallback callback, int messageExpiryInterval) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Persisting retained msg asynchronously - {} with expiry interval {}", topic, retainedMsgProto, messageExpiryInterval);
        }
        persist(topic, retainedMsgProto, messageExpiryInterval, callback);
    }

    @Override
    public void persistRetainedMsgSync(String topic, RetainedMsgProto retainedMsgProto) throws QueuePersistenceException {
        // only the cluster consumer calls this, to write its Kafka init marker; there is no marker to write here
    }

    private void persist(String topic, RetainedMsgProto retainedMsgProto, Integer messageExpiryInterval, BasicCallback callback) {
        StoreStateSupport.writeThenCallback(
                () -> write(topic, retainedMsgProto, messageExpiryInterval),
                callback,
                e -> log.error("[{}] Failed to store retained msg {}", topic, retainedMsgProto, e));
    }

    private void write(String topic, RetainedMsgProto retainedMsgProto, Integer messageExpiryInterval) {
        if (isEmpty(retainedMsgProto)) {
            store.delete(topic);
        } else {
            store.put(topic, RetainedMsgEnvelope.encode(messageExpiryInterval, retainedMsgProto));
        }
    }

    // the same test as the cluster consumer: a retained message with no payload clears the topic
    private boolean isEmpty(RetainedMsgProto retainedMsgProto) {
        return retainedMsgProto.getPayload().isEmpty() && retainedMsgProto.getQos() == 0;
    }

}
