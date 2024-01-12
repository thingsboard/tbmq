package org.thingsboard.mqtt.broker.common.data.mqtt;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MqttPubMsgWithCreatedTime {

    private MqttPublishMessage mqttPublishMessage;
    private long createdTime;
}
