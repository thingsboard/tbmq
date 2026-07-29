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
package org.thingsboard.mqtt.broker.service.queue;

import org.thingsboard.mqtt.broker.common.data.BasicCallback;

public interface IntegrationTopicService {

    String createTopic(String integrationId);

    void deleteTopic(String integrationId, BasicCallback callback);

    void deleteConsumerGroup(String integrationId);

    String getConsumerGroup(String integrationId);

    /**
     * Provisions the integration's dedicated lifecycle-events topic.
     * <p>
     * Unlike the data stream, the events stream never provisions its topic implicitly: its producer is configured
     * with {@code createTopicIfNotExists(false)} (see {@code KafkaIntegrationMsgQueueFactory.createEventProducer})
     * because events are sent synchronously on the MQTT processing thread, where a blocking admin call is not
     * acceptable - and where a missing topic stalls the send for {@code max.block.ms} before the event is dropped.
     * Every path that registers an opt-in is therefore responsible for calling this, off the hot path. All such
     * calls are idempotent.
     */
    String createEventTopic(String integrationId);

    /**
     * Same as {@link #createEventTopic(String)}, but consults the cached topic list first and so costs no admin
     * round-trip once the topic is known to exist. Suited to bulk provisioning where the cache is fresh; prefer
     * {@link #createEventTopic(String)} when the topic may have been deleted by another node, since the cache can
     * report a deleted topic as existing for up to {@code queue.kafka.admin.topics-cache-ttl-ms}.
     */
    String createEventTopicIfNotExists(String integrationId);

    void deleteEventTopic(String integrationId, BasicCallback callback);

    String getEventConsumerGroup(String integrationId);

}
