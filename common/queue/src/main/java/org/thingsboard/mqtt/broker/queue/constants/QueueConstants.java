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
package org.thingsboard.mqtt.broker.queue.constants;

import org.thingsboard.mqtt.broker.gen.queue.BlockedClientProto;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionInfoProto;
import org.thingsboard.mqtt.broker.gen.queue.RetainedMsgProto;

public class QueueConstants {

    public static final String TBMQ_NOT_IMPLEMENTED = "Implementation is not expected here for TBMQ!";
    public static final String TBMQ_IE_NOT_IMPLEMENTED = "Implementation is not expected here for TBMQ IE!";

    public static final String REPLICATION_FACTOR = "replication.factor";
    public static final String PARTITIONS = "partitions";
    public final static String CLEANUP_POLICY_PROPERTY = "cleanup.policy";
    public final static String COMPACT_POLICY = "compact";

    public static final ClientSessionInfoProto EMPTY_CLIENT_SESSION_INFO_PROTO = ClientSessionInfoProto.newBuilder().build();
    public static final RetainedMsgProto EMPTY_RETAINED_MSG_PROTO = RetainedMsgProto.newBuilder().build();
    public static final BlockedClientProto EMPTY_BLOCKED_CLIENT_PROTO = BlockedClientProto.getDefaultInstance();

    /**
     * TBMQ Integration executor downlink topics constants
     */
    public static final String HTTP_TOPIC_SUFFIX = ".http";
    public static final String MQTT_TOPIC_SUFFIX = ".mqtt";
    public static final String KAFKA_TOPIC_SUFFIX = ".kafka";

    public static final String IE_DOWNLINK_TOPIC = "tbmq.ie.downlink";
}
