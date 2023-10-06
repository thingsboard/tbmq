/**
 * Copyright © 2016-2023 The Thingsboard Authors
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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(exclude = "qos")
@ToString
public class TopicSharedSubscription {

    private final String topicFilter;
    private final String shareName;
    private final int qos;

    public TopicSharedSubscription(String topicFilter, String shareName) {
        this.topicFilter = topicFilter;
        this.shareName = shareName;
        this.qos = 0;
    }

    public String getKey() {
        return "ss_" + this.shareName + "_" + this.topicFilter;
    }
}
