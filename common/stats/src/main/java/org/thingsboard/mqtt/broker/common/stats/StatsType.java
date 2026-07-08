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
package org.thingsboard.mqtt.broker.common.stats;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;

@RequiredArgsConstructor
@Getter
public enum StatsType {

    MSG_DISPATCHER_PRODUCER("incomingPublishMsg.published"),
    CLIENT_SESSION_EVENT_CONSUMER("clientSessionEvent"),
    PUBLISH_MSG_CONSUMER("incomingPublishMsg.consumed"),
    NON_WRITABLE_CLIENTS("nonWritableClients"),
    SUBSCRIPTION_TOPIC_TRIE_SIZE("subscriptionTopicTrieSize"),
    RETAIN_MSG_TRIE_SIZE("retainMsgTrieSize"),
    LAST_WILL_CLIENTS("lastWillClients"),
    // Live MQTT channels on THIS node only. NOT the analog of the historical 'sessions' chart, which
    // counts cluster-wide sessions incl. offline persistent ones (see ALL_CLIENT_SESSIONS).
    CONNECTED_SESSIONS("connectedSessions"),
    CONNECTED_SSL_SESSIONS("connectedSslSessions"),
    // Cluster-wide client sessions incl. offline persistent ones. This is the Prometheus analog of the
    // historical 'sessions' chart (BrokerConstants.SESSIONS).
    ALL_CLIENT_SESSIONS("allClientSessions"),
    // Total subscription count across all clients (sum of the per-client subscription set sizes), matching
    // the historical 'subscriptions' chart (BrokerConstants.SUBSCRIPTIONS). Renamed from 'clientSubscriptions',
    // which misleadingly reported the number of clients with at least one subscription rather than a count.
    SUBSCRIPTIONS("subscriptions"),
    RETAINED_MESSAGES("retainedMessages"),
    SUBSCRIPTION_TRIE_NODES("subscriptionTrieNodes"),
    RETAIN_MSG_TRIE_NODES("retainMsgTrieNodes"),
    ACTIVE_APP_PROCESSORS("activeAppProcessors"),
    ACTIVE_SHARED_APP_PROCESSORS("activeSharedAppProcessors"),
    APP_PROCESSOR("appProcessor"),
    DEVICE_PROCESSOR("deviceProcessor"),
    RUNNING_ACTORS("runningActors"),
    SQL_QUEUE("sqlQueue"),
    CLIENT_SUBSCRIPTIONS_CONSUMER("clientSubscriptionsConsumer"),
    RETAINED_MSG_CONSUMER("retainedMsgConsumer"),
    CLIENT_ACTOR("clientActor"),
    ACTORS_PROCESSING("actors.processing"),
    FLOW_CONTROL("flowControl"),

    SUBSCRIPTION_LOOKUP("subscriptionLookup"),
    RETAINED_MSG_LOOKUP("retainedMsgLookup"),
    CLIENT_SESSIONS_LOOKUP("clientSessionsLookup"),
    NOT_PERSISTENT_MESSAGES_PROCESSING("notPersistentMessagesProcessing"),
    PERSISTENT_MESSAGES_PROCESSING("persistentMessagesProcessing"),
    DELIVERY("delivery"),

    QUEUE_PRODUCER("kafkaProducer.send"),
    QUEUE_CONSUMER("kafkaConsumer.commit"),

    IE_UPLINK_PRODUCER("ie.uplink.published"),
    INTEGRATION("integration"),
    INTEGRATION_PROCESSOR("integrationProcessor"),
    INTEGRATION_EVENT_PROCESSOR("integrationEventProcessor"),

    DROPPED_MSGS(BrokerConstants.DROPPED_MSGS),
    DROPPED_LIFECYCLE_EVENTS("droppedLifecycleEvents"),
    CLIENT_DISCONNECTS("clientDisconnects"),
    ;

    private final String printName;

}
