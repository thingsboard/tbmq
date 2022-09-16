/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.subscription.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedSubscriptionTopicFilterTest {

    @Test
    void testSharedSubscriptionTopicFilterEquals() {
        SharedSubscriptionTopicFilter sharedSubscriptionTopicFilter1 = getSubscriptionTopicFilter(0);
        SharedSubscriptionTopicFilter sharedSubscriptionTopicFilter2 = getSubscriptionTopicFilter(1);
        assertEquals(sharedSubscriptionTopicFilter1, sharedSubscriptionTopicFilter2);
    }

    private SharedSubscriptionTopicFilter getSubscriptionTopicFilter(int qos) {
        return new SharedSubscriptionTopicFilter("topicFilter", "shareName", qos);
    }
}