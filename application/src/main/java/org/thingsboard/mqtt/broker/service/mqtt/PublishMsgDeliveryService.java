package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

public interface PublishMsgDeliveryService {
    void sendPublishMsgToClient(ClientSessionCtx sessionCtx, int packetId, String topic, MqttQoS qos, byte[] payload);
}
