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
package org.thingsboard.mqtt.broker.queue.kafka.settings;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;

public abstract class TbAbstractKafkaSettings implements TbKafkaSettings {

    @Setter
    @Getter
    @Value("${queue.kafka.bootstrap.servers}")
    private String servers;

    @Override
    public String getAcks() {
        return getProducerSettings().getAcks();
    }

    @Override
    public int getRetries() {
        return getProducerSettings().getRetries();
    }

    @Override
    public int getBatchSize() {
        return getProducerSettings().getBatchSize();
    }

    @Override
    public long getLingerMs() {
        return getProducerSettings().getLingerMs();
    }

    @Override
    public long getBufferMemory() {
        return getProducerSettings().getBufferMemory();
    }

    @Override
    public int getMaxPollRecords() {
        return getConsumerSettings().getMaxPollRecords();
    }

    @Override
    public int getMaxPollIntervalMs() {
        return getConsumerSettings().getMaxPollIntervalMs();
    }

    @Override
    public int getMaxPartitionFetchBytes() {
        return getConsumerSettings().getMaxPartitionFetchBytes();
    }

    @Override
    public int getFetchMaxBytes() {
        return getConsumerSettings().getFetchMaxBytes();
    }

    abstract KafkaProducerSettings getProducerSettings();

    abstract KafkaConsumerSettings getConsumerSettings();
}
