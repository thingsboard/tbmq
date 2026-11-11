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
package org.thingsboard.mqtt.broker.service.mqtt.persistence.application.topic;

import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;

@Service
@StandaloneComponent
public class NoOpApplicationTopicService implements ApplicationTopicService {

    private static final String NOT_SUPPORTED = "APPLICATION clients are not supported in TBMQ Lite";

    @Override
    public String createTopic(String clientId) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public void createSharedTopic(ApplicationSharedSubscription subscription) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public void deleteTopic(String clientId, BasicCallback callback) {
        callback.onSuccess();
    }

    @Override
    public void deleteSharedTopic(ApplicationSharedSubscription subscription) {
    }

}
