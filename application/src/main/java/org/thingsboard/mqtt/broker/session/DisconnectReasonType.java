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
package org.thingsboard.mqtt.broker.session;

import java.util.EnumSet;
import java.util.Set;

public enum DisconnectReasonType {

    ON_DISCONNECT_MSG,
    ON_DISCONNECT_AND_WILL_MSG,
    ON_CONFLICTING_SESSIONS,
    ON_CLUSTER_CONFLICTING_SESSIONS,
    ON_ERROR,
    ON_CHANNEL_CLOSED,
    ON_RATE_LIMITS,
    ON_KEEP_ALIVE,
    ON_PROTOCOL_ERROR,
    ON_MALFORMED_PACKET,
    ON_QUOTA_EXCEEDED,
    ON_SUBSCRIPTION_ID_NOT_SUPPORTED,
    ON_PACKET_TOO_LARGE,
    ON_TOPIC_ALIAS_INVALID,
    ON_RECEIVE_MAXIMUM_EXCEEDED,
    ON_ADMINISTRATIVE_ACTION,
    ON_NOT_AUTHORIZED,
    ON_SERVER_BUSY,
    ON_SERVER_SHUTTING_DOWN,
    ON_TOPIC_FILTER_INVALID,
    ON_TOPIC_NAME_INVALID,
    ON_PAYLOAD_FORMAT_INVALID,
    ON_RETAIN_NOT_SUPPORTED,
    ON_QOS_NOT_SUPPORTED,
    ON_USE_ANOTHER_SERVER,
    ON_SERVER_MOVED,
    ON_SHARED_SUBSCRIPTIONS_NOT_SUPPORTED,
    ON_CONNECTION_RATE_EXCEEDED,
    ON_MAXIMUM_CONNECT_TIME,
    ON_WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED,
    ON_CONNECTION_FAILURE;

    /** Reasons for which we should NOT send a server-initiated DISCONNECT packet. */
    public static final Set<DisconnectReasonType> SERVER_DISCONNECT_EXCLUSIONS =
            EnumSet.of(ON_DISCONNECT_MSG, ON_CHANNEL_CLOSED, ON_CONNECTION_FAILURE);

    public boolean allowsServerDisconnect() {
        return !SERVER_DISCONNECT_EXCLUSIONS.contains(this);
    }

    public boolean allowsLastWillOnDisconnect() {
        return ON_DISCONNECT_MSG != this;
    }

    public boolean isNotConflictingSession() {
        return ON_CONFLICTING_SESSIONS != this;
    }

    public boolean isNotClusterConflictingSession() {
        return ON_CLUSTER_CONFLICTING_SESSIONS != this;
    }
}
