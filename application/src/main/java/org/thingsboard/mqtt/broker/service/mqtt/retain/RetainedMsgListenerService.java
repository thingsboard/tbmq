/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;

import java.util.List;
import java.util.Map;

public interface RetainedMsgListenerService {

    void init(Map<String, RetainedMsg> retainedMsgMap);

    boolean isInitialized();

    void startListening(RetainedMsgConsumer retainedMsgConsumer);

    void cacheRetainedMsgAndPersist(String topic, RetainedMsg retainedMsg);

    void cacheRetainedMsgAndPersist(String topic, RetainedMsg retainedMsg, BasicCallback callback);

    void cacheRetainedMsg(String topic, RetainedMsg retainedMsg);

    void clearRetainedMsgAndPersist(String topic);

    void clearRetainedMsgAndPersist(String topic, BasicCallback callback);

    void clearRetainedMsg(String topic);

    RetainedMsgDto getRetainedMsgForTopic(String topic);

    List<RetainedMsg> getRetainedMessages();

}
