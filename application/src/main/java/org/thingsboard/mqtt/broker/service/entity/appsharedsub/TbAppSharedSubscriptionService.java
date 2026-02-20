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
package org.thingsboard.mqtt.broker.service.entity.appsharedsub;

import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;

public interface TbAppSharedSubscriptionService {

    ApplicationSharedSubscription save(ApplicationSharedSubscription sharedSubscription, User currentUser) throws ThingsboardException;

    void delete(ApplicationSharedSubscription sharedSubscription, User currentUser);

    void createKafkaTopics(User currentUser);

    void deleteKafkaTopics(User currentUser);

}
