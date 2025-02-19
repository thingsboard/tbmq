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
package org.thingsboard.mqtt.broker.dao.sql.event;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class EventPartitionConfiguration {

    @Getter
    @Value("${sql.events.partition_size:168}")
    private int regularPartitionSizeInHours;

    private long regularPartitionSizeInMs;

    @PostConstruct
    public void init() {
        regularPartitionSizeInMs = TimeUnit.HOURS.toMillis(regularPartitionSizeInHours);
    }

    public long getPartitionSizeInMs() {
        return regularPartitionSizeInMs;
    }
}
