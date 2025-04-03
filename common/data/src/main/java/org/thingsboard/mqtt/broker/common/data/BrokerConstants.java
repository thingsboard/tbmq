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
package org.thingsboard.mqtt.broker.common.data;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class BrokerConstants {

    public static final String TCP = "TCP";
    public static final String SSL = "SSL";
    public static final String WS = "WS";
    public static final String WSS = "WSS";

    public static final String ASC_ORDER = "ASC";
    public static final String DESC_ORDER = "DESC";

    public static final String ENTITY_ID_TOTAL = "total";
    public static final String INCOMING_MSGS = "incomingMsgs";
    public static final String OUTGOING_MSGS = "outgoingMsgs";
    public static final String DROPPED_MSGS = "droppedMsgs";
    public static final String SESSIONS = "sessions";
    public static final String SUBSCRIPTIONS = "subscriptions";
    public static final String PROCESSED_BYTES = "processedBytes";

    public static final List<String> MSG_RELATED_HISTORICAL_KEYS = List.of(INCOMING_MSGS, OUTGOING_MSGS, DROPPED_MSGS, PROCESSED_BYTES);

    public static final List<String> HISTORICAL_KEYS = List.of(INCOMING_MSGS, OUTGOING_MSGS, DROPPED_MSGS, SESSIONS, SUBSCRIPTIONS, PROCESSED_BYTES);

    public static final String RECEIVED_PUBLISH_MSGS = "receivedPubMsgs";
    public static final String QOS_0_RECEIVED_PUBLISH_MSGS = "qos0ReceivedPubMsgs";
    public static final String QOS_1_RECEIVED_PUBLISH_MSGS = "qos1ReceivedPubMsgs";
    public static final String QOS_2_RECEIVED_PUBLISH_MSGS = "qos2ReceivedPubMsgs";
    public static final String SENT_PUBLISH_MSGS = "sentPubMsgs";
    public static final String QOS_0_SENT_PUBLISH_MSGS = "qos0SentPubMsgs";
    public static final String QOS_1_SENT_PUBLISH_MSGS = "qos1SentPubMsgs";
    public static final String QOS_2_SENT_PUBLISH_MSGS = "qos2SentPubMsgs";

    public static final List<String> CLIENT_SESSION_METRIC_KEYS = List.of(RECEIVED_PUBLISH_MSGS, QOS_0_RECEIVED_PUBLISH_MSGS,
            QOS_1_RECEIVED_PUBLISH_MSGS, QOS_2_RECEIVED_PUBLISH_MSGS,
            SENT_PUBLISH_MSGS, QOS_0_SENT_PUBLISH_MSGS, QOS_1_SENT_PUBLISH_MSGS, QOS_2_SENT_PUBLISH_MSGS);

    public static String getQosReceivedStatsKey(int qos) {
        return switch (qos) {
            case 0 -> QOS_0_RECEIVED_PUBLISH_MSGS;
            case 1 -> QOS_1_RECEIVED_PUBLISH_MSGS;
            default -> QOS_2_RECEIVED_PUBLISH_MSGS;
        };
    }

    public static String getQosSentStatsKey(int qos) {
        return switch (qos) {
            case 0 -> QOS_0_SENT_PUBLISH_MSGS;
            case 1 -> QOS_1_SENT_PUBLISH_MSGS;
            default -> QOS_2_SENT_PUBLISH_MSGS;
        };
    }

    public static final String MEMORY_USAGE = "memoryUsage";
    public static final String CPU_USAGE = "cpuUsage";
    public static final String DISK_USAGE = "discUsage";
    public static final String TOTAL_MEMORY = "totalMemory";
    public static final String CPU_COUNT = "cpuCount";
    public static final String TOTAL_DISK_SPACE = "totalDiscSpace";

    public static final List<String> SERVICE_INFO_KEYS = List.of(MEMORY_USAGE, CPU_USAGE,
            DISK_USAGE, TOTAL_MEMORY,
            CPU_COUNT, TOTAL_DISK_SPACE);

    public static final byte[] LOCAL_ADR;

    static {
        try {
            LOCAL_ADR = InetAddress.getLocalHost().getAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static final int WRITE_BUFFER_DEFAULT_HIGH_WATER_MARK = 64 * 1024; // 64 KB
    public static final int WRITE_BUFFER_DEFAULT_LOW_WATER_MARK = 32 * 1024; // 32 KB

    public static final int TCP_PACKET_BYTES_OVERHEAD = 66;
    public static final int TLS_PACKET_BYTES_OVERHEAD = 95;
    public static final int TLS_CONNECT_BYTES_OVERHEAD = 4078;
    public static final int TLS_DISCONNECT_BYTES_OVERHEAD = 364;

    public static final int WS_MAX_CONTENT_LENGTH = 65536;
    public static final String WS_PATH = "/mqtt";

    public static final int DEFAULT_PAGE_SIZE = 1000;
    public static final int MAX_IN_FLIGHT_MESSAGES = DEFAULT_PAGE_SIZE;
    public static final int DELAYED_MSG_QUEUE_MAX_SIZE = DEFAULT_PAGE_SIZE;

    public static final String MQTT_PROTOCOL_NAME = "MQTT";
    public static final String MQTT_V_3_1_PROTOCOL_NAME = "MQIsdp";

    public static final int DEFAULT_RECEIVE_MAXIMUM = 65535;
    public static final int SUBSCRIPTION_ID_MAXIMUM = 268_435_455;

    public static final String NULL_CHAR_STR = "\u0000";

    public static final char TOPIC_DELIMITER = '/';
    public static final String TOPIC_DELIMITER_STR = "/";
    public static final String MULTI_LEVEL_WILDCARD = "#";
    public static final String SINGLE_LEVEL_WILDCARD = "+";
    public static final String SHARED_SUBSCRIPTION_PREFIX = "$share/";
    public static final int SHARE_NAME_IDX = SHARED_SUBSCRIPTION_PREFIX.length();

    public static final String MESSAGE_EXPIRY_INTERVAL = "messageExpiryInterval";
    public static final String CREATED_TIME = "createdTime";

    public static final int PAYLOAD_FORMAT_INDICATOR_PROP_ID = 1;
    public static final int PUB_EXPIRY_INTERVAL_PROP_ID = 2;
    public static final int CONTENT_TYPE_PROP_ID = 3;
    public static final int RESPONSE_TOPIC_PROP_ID = 8;
    public static final int CORRELATION_DATA_PROP_ID = 9;
    public static final int SUBSCRIPTION_IDENTIFIER_PROP_ID = 11;
    public static final int SESSION_EXPIRY_INTERVAL_PROP_ID = 17;
    public static final int ASSIGNED_CLIENT_IDENTIFIER_PROP_ID = 18;
    public static final int SERVER_KEEP_ALIVE_PROP_ID = 19;
    public static final int AUTHENTICATION_METHOD_PROP_ID = 21;
    public static final int AUTHENTICATION_DATA_PROP_ID = 22;
    public static final int REQUEST_PROBLEM_INFORMATION_PROP_ID = 23;
    public static final int WILL_DELAY_INTERVAL_PROP_ID = 24;
    public static final int REQUEST_RESPONSE_INFO_PROP_ID = 25;
    public static final int RESPONSE_INFORMATION_PROP_ID = 26;
    public static final int RECEIVE_MAXIMUM_PROP_ID = 33;
    public static final int TOPIC_ALIAS_MAX_PROP_ID = 34;
    public static final int TOPIC_ALIAS_PROP_ID = 35;
    public static final int MAXIMUM_QOS_PROP_ID = 36;
    public static final int RETAIN_AVAILABLE_PROP_ID = 37;
    public static final int USER_PROPERTY_PROP_ID = 38;
    public static final int MAXIMUM_PACKET_SIZE_PROP_ID = 39;
    public static final int WILDCARD_SUBSCRIPTION_AVAILABLE_PROP_ID = 40;
    public static final int SUBSCRIPTION_IDENTIFIER_AVAILABLE_PROP_ID = 41;
    public static final int SHARED_SUBSCRIPTION_AVAILABLE_PROP_ID = 42;

    public static final String SERVICE_ID_HEADER = "serviceId";
    public static final String EMPTY_STR = "";

    public static final String DOT = ".";
    public static final String COMMA = ",";
    public static final String COLON = ":";
    public static final String SEMICOLON = ";";
    public static final String HYPHEN = "-";

    //client session event constants
    public static final String REQUEST_ID_HEADER = "requestId";
    public static final String RESPONSE_TOPIC_HEADER = "responseTopic";
    public static final String REQUEST_TIME = "requestTime";

    public static final String SUBSCRIPTION_ID_IS_0_ERROR_MSG = "It is a Protocol Error if the Subscription Identifier has a value of 0";
    public static final String FAILED_TO_CONNECT_CLIENT_MSG = "Failed to connect client";

    public static final String BASIC_DOWNLINK_CG_PREFIX = "basic-downlink-msg-consumer-group-";
    public static final String PERSISTED_DOWNLINK_CG_PREFIX = "persisted-downlink-msg-consumer-group-";
    public static final String CLIENT_SESSION_CG_PREFIX = "client-session-consumer-group-";
    public static final String CLIENT_SUBSCRIPTIONS_CG_PREFIX = "client-subscriptions-consumer-group-";
    public static final String RETAINED_MSG_CG_PREFIX = "retained-msg-consumer-group-";

    public static final String SYSTEMS_TOPIC_PREFIX = "$SYS/tbmq/";
    public static final String CLEANUP_CLIENT_SESSION_STATS_TOPIC_NAME = SYSTEMS_TOPIC_PREFIX + "cs/stats/cleanup";
    public static final String LATEST_VERSION_AVAILABLE_TOPIC_NAME = SYSTEMS_TOPIC_PREFIX + "latest/version/available";

    public static final String UNKNOWN = "unknown";

    public static final int BLANK_PACKET_ID = -1;
    public static final int MAX_PACKET_ID = 0xffff;

    public static final String PUB_SUB_AUTH_RULES_ALLOW_ALL = ".*";
    public static final String WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME = "TBMQ WebSockets MQTT Credentials";
    public static final String WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_USERNAME = "tbmq_websockets_username";

    public static final String WEB_SOCKET_DEFAULT_CONNECTION_NAME = "WebSocket Default Connection";

    public static final String WEB_SOCKET_DEFAULT_SUBSCRIPTION_TOPIC_FILTER = "sensors/#";
    public static final int WEB_SOCKET_DEFAULT_SUBSCRIPTION_QOS = 1;
    public static final String WEB_SOCKET_DEFAULT_SUBSCRIPTION_COLOR = "#34920C";

    public static final String SYSTEM_DUMMY_CLIENT_ID_PREFIX = "tbmq_system_dummy_client_id_";
    public static final String SYSTEM_DUMMY_TOPIC_FILTER = "tbmq_system_dummy_topic_filter";

    public static final byte[] DUMMY_PAYLOAD = "test".getBytes(StandardCharsets.UTF_8);

    public static final String MQTT_CONNECTIVITY = "mqtt";
    public static final String MQTTS_CONNECTIVITY = "mqtts";
    public static final String WS_CONNECTIVITY = "ws";
    public static final String WSS_CONNECTIVITY = "wss";

    public static final String CONNECTIVITY_KEY = "connectivity";
    public static final String WEBSOCKET_KEY = "websocket";
}
