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
package org.thingsboard.mqtt.broker.session;

public enum DisconnectReasonType {

    ON_DISCONNECT_MSG,
    ON_DISCONNECT_AND_WILL_MSG,
    ON_CONFLICTING_SESSIONS,
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
    ON_RECEIVE_MAXIMUM_EXCEEDED

}
