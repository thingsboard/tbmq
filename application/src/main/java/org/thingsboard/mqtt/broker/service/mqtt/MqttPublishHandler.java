/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.constant.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.exception.NotSupportedQoSLevelException;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgMetadata;
import org.thingsboard.mqtt.broker.service.mqtt.validation.TopicValidationService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionDisconnectListener;
import org.thingsboard.mqtt.broker.service.processing.MsgDispatcherService;

import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class MqttPublishHandler {
    private final MqttMessageGenerator mqttMessageGenerator;
    private final MsgDispatcherService msgDispatcherService;
    private final TopicValidationService topicValidationService;

    public void process(ClientSessionCtx ctx, MqttPublishMessage msg, SessionDisconnectListener disconnectListener) throws MqttException {
        validatePublish(msg);
        UUID sessionId = ctx.getSessionId();
        int msgId = msg.variableHeader().packetId();

        log.trace("[{}] Processing publish msg: {}", sessionId, msgId);

        msgDispatcherService.acknowledgePublishMsg(ctx.getSessionInfoProto(), msg, new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.trace("[{}] Successfully acknowledged msg: {}", sessionId, msgId);
                acknowledgeMsg(ctx, msg);
            }

            @Override
            public void onFailure(Throwable t) {
                log.trace("[{}] Failed to publish msg: {}", sessionId, msg, t);
                disconnectListener.onSessionDisconnect();
            }
        });
    }


    private void validatePublish(MqttPublishMessage mqttMsg) {
        MqttQoS mqttQoS = mqttMsg.fixedHeader().qosLevel();
        if (mqttQoS.value() > BrokerConstants.MAX_SUPPORTED_QOS_LVL.value()) {
            throw new NotSupportedQoSLevelException("QoS level " + mqttQoS + " is not supported.");
        }
        topicValidationService.validateTopic(mqttMsg.variableHeader().topicName());
    }

    private void acknowledgeMsg(ClientSessionCtx ctx, MqttPublishMessage mqttMsg) {
        MqttQoS mqttQoS = mqttMsg.fixedHeader().qosLevel();
        switch (mqttQoS) {
            case AT_MOST_ONCE:
                break;
            case AT_LEAST_ONCE:
                ctx.getChannel().writeAndFlush(mqttMessageGenerator.createPubAckMsg(mqttMsg.variableHeader().packetId()));
                break;
            default:
                throw new NotSupportedQoSLevelException("QoS level " + mqttQoS + " is not supported.");
        }
    }

}
