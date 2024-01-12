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
