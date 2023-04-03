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
package org.thingsboard.mqtt.broker.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class BrokerConstants {

    public static final byte[] LOCAL_ADR;

    static {
        try {
            LOCAL_ADR = InetAddress.getLocalHost().getAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static final int DEFAULT_PAGE_SIZE = 1000;

    public static final String MQTT_PROTOCOL_NAME = "MQTT";
    public static final String MQTT_V_3_1_PROTOCOL_NAME = "MQIsdp";

    public static final char TOPIC_DELIMITER = '/';
    public static final String MULTI_LEVEL_WILDCARD = "#";
    public static final String SINGLE_LEVEL_WILDCARD = "+";
    public static final String SHARED_SUBSCRIPTION_PREFIX = "$share/";
    public static final int SHARE_NAME_IDX = SHARED_SUBSCRIPTION_PREFIX.length();

    public static final String SERVICE_ID_HEADER = "serviceId";
    public static final String EMPTY_STR = "";

    //client session event constants
    public static final String REQUEST_ID_HEADER = "requestId";
    public static final String RESPONSE_TOPIC_HEADER = "responseTopic";
    public static final String REQUEST_TIME = "requestTime";

    public static final String FAILED_TO_CONNECT_CLIENT_MSG = "Failed to connect client";

    public static final String BASIC_DOWNLINK_CG_PREFIX = "basic-downlink-publish-group-";
    public static final String PERSISTENT_DOWNLINK_CG_PREFIX = "persistent-downlink-publish-group-";
    public static final String CLIENT_SESSION_CG_PREFIX = "client-session-group-";
    public static final String CLIENT_SUBSCRIPTIONS_CG_PREFIX = "client-subscriptions-group-";
    public static final String RETAINED_MSG_CG_PREFIX = "retained-msg-group-";
}
