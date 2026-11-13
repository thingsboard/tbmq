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
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.dao.store.BrokerStateStores;
import org.thingsboard.mqtt.broker.dao.store.KeyValueStore;
import org.thingsboard.mqtt.broker.exception.QueuePersistenceException;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsg;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgChangesCallback;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgConsumer;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import java.util.Map;

/**
 * Loads the retained messages from the embedded store at startup. There is only one node, so nobody else changes
 * them and {@link #listen} has nothing to deliver.
 */
@Slf4j
@Component
@StandaloneComponent
public class StandaloneRetainedMsgConsumer implements RetainedMsgConsumer {

    private final KeyValueStore store;

    public StandaloneRetainedMsgConsumer(BrokerStateStores stores) {
        this.store = stores.retainedMessages();
    }

    @Override
    public Map<String, RetainedMsg> initLoad() throws QueuePersistenceException {
        log.debug("Starting retained messages initLoad");
        Map<String, RetainedMsg> retainedMsgs = StoreStateSupport.loadAll(store, "retained messages", StandaloneRetainedMsgConsumer::parse);
        log.debug("Finished retained messages initLoad, loaded {} messages", retainedMsgs.size());
        return retainedMsgs;
    }

    @Override
    public void listen(RetainedMsgChangesCallback callback) {
        // single node: no other writer, nothing to listen to
    }

    private static RetainedMsg parse(byte[] bytes) throws Exception {
        RetainedMsgEnvelope.Decoded decoded = RetainedMsgEnvelope.decode(bytes);
        RetainedMsg retainedMsg = ProtoConverter.convertProtoToRetainedMsg(decoded.proto());
        if (decoded.messageExpiryInterval() != null) {
            MqttPropertiesUtil.addMsgExpiryIntervalToProps(retainedMsg.getProperties(), decoded.messageExpiryInterval());
        }
        return retainedMsg;
    }

}
