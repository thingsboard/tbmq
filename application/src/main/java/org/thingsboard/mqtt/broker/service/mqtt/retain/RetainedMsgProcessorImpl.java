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

import io.netty.handler.codec.mqtt.MqttProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.NettyMqttConverter;
import org.thingsboard.mqtt.broker.dao.store.StoreException;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;

import static org.thingsboard.mqtt.broker.common.data.util.CallbackUtil.createCallback;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetainedMsgProcessorImpl implements RetainedMsgProcessor {

    private final RetainedMsgListenerService retainedMsgListenerService;

    @Override
    public PublishMsg process(PublishMsg publishMsg) {
        if (publishMsg.getByteBuf() != null && publishMsg.getPayload() == null) {
            byte[] payload = NettyMqttConverter.toBytes(publishMsg.getByteBuf());
            publishMsg = publishMsg.toBuilder().payload(payload).build();
        }
        return processRetainedMsg(publishMsg);
    }

    /**
     * @throws StoreException if the store failed to write the retained msg before the call returned; the retained msg
     *                        of the topic is then unchanged, and the publish must fail
     */
    private PublishMsg processRetainedMsg(PublishMsg publishMsg) {
        String topic = publishMsg.getTopicName();
        StoreFailureTrackingCallback callback;
        if (payloadIsEmpty(publishMsg)) {
            callback = new StoreFailureTrackingCallback(createCallback(
                    () -> log.trace("[{}] Persisted cleared retained msg", topic),
                    t -> log.warn("[{}] Failed to persist cleared retained msg", topic, t)));
            retainedMsgListenerService.clearRetainedMsgAndPersist(topic, callback);
        } else {
            callback = new StoreFailureTrackingCallback(createCallback(
                    () -> log.trace("[{}] Persisted retained msg", topic),
                    t -> log.warn("[{}] Failed to persist retained msg", topic, t)));
            retainedMsgListenerService.cacheRetainedMsgAndPersist(topic, newRetainedMsg(publishMsg), callback);
        }
        callback.throwIfStoreFailed();
        return publishMsg;
    }

    private boolean payloadIsEmpty(PublishMsg publishMsg) {
        return publishMsg.getPayload().length == 0;
    }

    private RetainedMsg newRetainedMsg(PublishMsg publishMsg) {
        MqttProperties properties = MqttPropertiesUtil.getMqttProperties(publishMsg);
        return new RetainedMsg(publishMsg.getTopicName(), publishMsg.getPayload(), publishMsg.getQos(), properties);
    }

}
