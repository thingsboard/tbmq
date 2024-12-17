/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.thingsboard.mqtt.broker.common.data.validation.NoXss;

import java.io.Serial;
import java.util.UUID;

@Data
@ToString
@EqualsAndHashCode(callSuper = true)
public class ApplicationSharedSubscription extends BaseData {

    @Serial
    private static final long serialVersionUID = -3332462179399001894L;

    @NoXss
    private String name;
    @NoXss
    private String topicFilter;
    private int partitions;

    public ApplicationSharedSubscription() {
    }

    public ApplicationSharedSubscription(UUID id) {
        super(id);
    }

    public ApplicationSharedSubscription(ApplicationSharedSubscription mqttClientCredentials) {
        super(mqttClientCredentials);
        this.name = mqttClientCredentials.getName();
        this.topicFilter = mqttClientCredentials.getTopicFilter();
        this.partitions = mqttClientCredentials.getPartitions();
    }

}
