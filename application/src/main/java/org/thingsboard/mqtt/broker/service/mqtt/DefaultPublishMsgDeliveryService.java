package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPublishMsgDeliveryService implements PublishMsgDeliveryService {
    private final MqttMessageGenerator mqttMessageGenerator;

    @Override
    public void sendPublishMsgToClient(ClientSessionCtx sessionCtx, int packetId, String topic, MqttQoS qos, byte[] payload) {
        MqttPublishMessage mqttPubMsg = mqttMessageGenerator.createPubMsg(packetId, topic, qos, payload);
        try {
            sessionCtx.getChannel().writeAndFlush(mqttPubMsg);
        } catch (Exception e) {
            if (sessionCtx.isConnected()) {
                log.debug("[{}][{}] Failed to send publish msg to MQTT client. Reason - {}.",
                        sessionCtx.getClientId(), sessionCtx.getSessionId(), e.getMessage());
                log.trace("Detailed error:", e);
            }
        }
    }
}
