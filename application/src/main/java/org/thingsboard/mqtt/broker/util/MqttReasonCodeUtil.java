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
package org.thingsboard.mqtt.broker.util;

import io.netty.handler.codec.mqtt.MqttReasonCodes;

import java.util.List;

public class MqttReasonCodeUtil {

    static final List<MqttReasonCodes.SubAck> GRANTED_QOS_LIST = List.of(
            MqttReasonCodes.SubAck.GRANTED_QOS_0,
            MqttReasonCodes.SubAck.GRANTED_QOS_1,
            MqttReasonCodes.SubAck.GRANTED_QOS_2);

    public static List<MqttReasonCodes.SubAck> getGrantedQosList() {
        return GRANTED_QOS_LIST;
    }

    public static int byteToInt(byte byteValue) {
        return Byte.toUnsignedInt(byteValue);
    }

    public static MqttReasonCodes.SubAck qosValueToReasonCode(int qos) {
        return MqttReasonCodes.SubAck.valueOf((byte) qos);
    }
}
