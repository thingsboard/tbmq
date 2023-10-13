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
package org.thingsboard.mqtt.broker.util;

import java.util.List;

public enum MqttReasonCode {

    GRANTED_QOS_0((byte) 0x00),
    GRANTED_QOS_1((byte) 0x01),
    GRANTED_QOS_2((byte) 0x02),
    DISCONNECT_WITH_WILL_MSG((byte) 0x04),
    SUCCESS((byte) 0x00),
    FAILURE((byte) 0x80),
    MALFORMED_PACKET((byte) 0x81),
    PROTOCOL_ERROR((byte) 0x82),
    IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83),
    NOT_AUTHORIZED((byte) 0x87),
    KEEP_ALIVE_TIMEOUT((byte) 0x8D),
    SESSION_TAKEN_OVER((byte) 0x8E),
    TOPIC_NAME_INVALID((byte) 0x90),
    PACKET_ID_NOT_FOUND((byte) 0x92),
    TOPIC_ALIAS_INVALID((byte) 0x94),
    PACKET_TOO_LARGE((byte) 0x95),
    MESSAGE_RATE_TOO_HIGH((byte) 0x96),
    QUOTA_EXCEEDED((byte) 0x97),
    ADMINISTRATIVE_ACTION((byte) 0x98),
    USE_ANOTHER_SERVER((byte) 0x9C),
    SERVER_MOVED((byte) 0x9D),
    SUBSCRIPTION_ID_NOT_SUPPORTED((byte) 0xA1),
    ;

    static final List<MqttReasonCode> GRANTED_QOS_LIST = List.of(GRANTED_QOS_0, GRANTED_QOS_1, GRANTED_QOS_2);

    private final byte byteValue;

    MqttReasonCode(byte byteValue) {
        this.byteValue = byteValue;
    }

    public byte value() {
        return byteValue;
    }

    public int intValue() {
        return byteValue;
    }

    public static List<MqttReasonCode> getGrantedQosList() {
        return GRANTED_QOS_LIST;
    }

    public static MqttReasonCode valueOf(int value) {
        for (MqttReasonCode code : values()) {
            if (code.byteValue == value) {
                return code;
            }
        }
        throw new IllegalArgumentException("invalid MqttReasonCode: " + value);
    }

}