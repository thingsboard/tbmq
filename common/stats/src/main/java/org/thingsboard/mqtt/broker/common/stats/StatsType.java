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
package org.thingsboard.mqtt.broker.common.stats;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum StatsType {

    MSG_DISPATCHER_PRODUCER("incomingPublishMsg.published"),
    CLIENT_SESSION_EVENT_CONSUMER("clientSessionEvent"),
    PUBLISH_MSG_CONSUMER("incomingPublishMsg.consumed"),
    SUBSCRIPTION_TOPIC_TRIE_SIZE("subscriptionTopicTrieSize"),
    RETAIN_MSG_TRIE_SIZE("retainMsgTrieSize"),
    LAST_WILL_CLIENTS("lastWillClients"),
    CONNECTED_SESSIONS("connectedSessions"),
    CONNECTED_SSL_SESSIONS("connectedSslSessions"),
    ALL_CLIENT_SESSIONS("allClientSessions"),
    CLIENT_SUBSCRIPTIONS("clientSubscriptions"),
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

    SUBSCRIPTION_LOOKUP("subscriptionLookup"),
    RETAINED_MSG_LOOKUP("retainedMsgLookup"),
    CLIENT_SESSIONS_LOOKUP("clientSessionsLookup"),
    NOT_PERSISTENT_MESSAGES_PROCESSING("notPersistentMessagesProcessing"),
    PERSISTENT_MESSAGES_PROCESSING("persistentMessagesProcessing"),
    DELIVERY("delivery"),

    QUEUE_PRODUCER("producer"),
    QUEUE_CONSUMER("consumer"),

    IE_UPLINK_PRODUCER("ie.uplink.published"),
    INTEGRATION("integration"),
    INTEGRATION_PROCESSOR("integrationProcessor"),
    ;

    private final String printName;

}
