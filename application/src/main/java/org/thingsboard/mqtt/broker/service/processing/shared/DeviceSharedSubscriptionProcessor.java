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
package org.thingsboard.mqtt.broker.service.processing.shared;

import org.thingsboard.mqtt.broker.service.subscription.Subscription;

import java.util.List;
import java.util.Set;

public interface DeviceSharedSubscriptionProcessor {

    /**
     * Processes device shared subscriptions to identify the target device subscriptions for shared topics.
     *
     * This method takes a set of device subscriptions and identifies one subscription
     * from each shared subscription group.
     *
     * @param deviceSubscriptions A set of device subscriptions for shared topics. May be empty or null.
     * @param qos The quality of service level of published message to apply for the identified subscriptions.
     * @return A list of target subscriptions, one per shared subscription group. Returns null if input subscriptions are empty or null.
     */
    List<Subscription> getTargetSubscriptions(Set<Subscription> deviceSubscriptions, int qos);

}
