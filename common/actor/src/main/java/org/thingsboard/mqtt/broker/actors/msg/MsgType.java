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
package org.thingsboard.mqtt.broker.actors.msg;

public enum MsgType {
    // Device Msg Types
    DEVICE_CONNECTED_EVENT_MSG,

    SHARED_SUBSCRIPTION_EVENT_MSG,

    DEVICE_DISCONNECTED_EVENT_MSG,

    PACKET_ACKNOWLEDGED_EVENT_MSG,

    PACKET_RECEIVED_EVENT_MSG,

    PACKET_COMPLETED_EVENT_MSG,

    INCOMING_PUBLISH_MSG,

    STOP_DEVICE_ACTOR_COMMAND_MSG,

    // Client Session MQTT Msg Types
    MQTT_CONNECT_MSG,
    MQTT_SUBSCRIBE_MSG,
    MQTT_UNSUBSCRIBE_MSG,
    MQTT_PING_MSG,
    MQTT_PUBLISH_MSG,
    MQTT_PUBACK_MSG,
    MQTT_PUBREC_MSG,
    MQTT_PUBREL_MSG,
    MQTT_PUBCOMP_MSG,

    PUBACK_RESPONSE_MSG,
    PUBREC_RESPONSE_MSG,

    // Client Session Inter-Node Management Msg Types
    SESSION_INIT_MSG,
    DISCONNECT_MSG,
    CONNECTION_ACCEPTED_MSG,

    // Client Actor Management Msg Types
    STOP_ACTOR_COMMAND_MSG,

    // Client Session Inter-Cluster Management Msg Types
    CLEAR_SESSION_MSG,
    SESSION_DISCONNECTED_MSG,
    CONNECTION_REQUEST_MSG,
    REMOVE_APPLICATION_TOPIC_REQUEST_MSG,

    SUBSCRIBE_COMMAND_MSG,
    UNSUBSCRIBE_COMMAND_MSG,

    SUBSCRIPTION_CHANGED_EVENT_MSG,
    ;
}
