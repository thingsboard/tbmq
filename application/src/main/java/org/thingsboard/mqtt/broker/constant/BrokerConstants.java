/**
 * Copyright © 2016-2022 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.constant;

import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

public class BrokerConstants {
    public static final char TOPIC_DELIMITER = '/';
    public static final String MULTI_LEVEL_WILDCARD = "#";
    public static final String SINGLE_LEVEL_WILDCARD = "+";


    public static final String SERVICE_ID_HEADER = "serviceId";

    public static final QueueProtos.ClientSessionInfoProto EMPTY_CLIENT_SESSION_INFO_PROTO = QueueProtos.ClientSessionInfoProto.newBuilder().build();

    //client session event constants
    public static final String REQUEST_ID_HEADER = "requestId";
    public static final String RESPONSE_TOPIC_HEADER = "responseTopic";
    public static final String REQUEST_TIME = "requestTime";

}
