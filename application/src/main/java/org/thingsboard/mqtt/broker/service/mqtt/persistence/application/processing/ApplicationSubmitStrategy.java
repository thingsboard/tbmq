/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public interface ApplicationSubmitStrategy {
    void init(List<PublishMsgWithOffset> messagesWithOffset);

    ConcurrentMap<Integer, PublishMsgWithOffset> getPendingMap();

    void process(Consumer<PublishMsgWithOffset> msgConsumer);

    void update(Map<Integer, PublishMsgWithOffset> reprocessMap);

    void onSuccess(Long offset);

    Long getLastCommittedOffset();
}
