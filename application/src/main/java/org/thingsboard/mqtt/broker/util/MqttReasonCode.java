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
package org.thingsboard.mqtt.broker.util;

import java.util.List;

public enum MqttReasonCode {

    GRANTED_QOS_0((byte) 0),
    GRANTED_QOS_1((byte) 1),
    GRANTED_QOS_2((byte) 2),
    SUCCESS((byte) 0),
    FAILURE((byte) 128),
    NOT_AUTHORIZED((byte) 135),
    TOPIC_NAME_INVALID((byte) 144),
    PACKET_ID_NOT_FOUND((byte) 146),
    ;

    static final List<MqttReasonCode> GRANTED_QOS_LIST = List.of(GRANTED_QOS_0, GRANTED_QOS_1, GRANTED_QOS_2);

    private final byte byteValue;

    MqttReasonCode(byte byteValue) {
        this.byteValue = byteValue;
    }

    public byte value() {
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