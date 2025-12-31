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
package org.thingsboard.mqtt.broker.service.mqtt.client.event;

import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ClearSessionMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.RemoveApplicationTopicRequestMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionClusterManagementMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventProto;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.REQUEST_ID_HEADER;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.REQUEST_TIME;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.RESPONSE_TOPIC_HEADER;
import static org.thingsboard.mqtt.broker.common.data.util.BytesUtil.bytesToLong;
import static org.thingsboard.mqtt.broker.common.data.util.BytesUtil.bytesToString;
import static org.thingsboard.mqtt.broker.common.data.util.BytesUtil.bytesToUuid;

@Service
public class ClientSessionCallbackMsgFactoryImpl implements ClientSessionCallbackMsgFactory {

    @Override
    public SessionClusterManagementMsg createSessionClusterManagementMsg(TbProtoQueueMsg<ClientSessionEventProto> msg, ClientCallback callback) {
        ClientSessionEventProto eventProto = msg.getValue();
        switch (ClientSessionEventType.valueOf(eventProto.getEventType())) {
            case CONNECTION_REQUEST -> {
                var sessionInfo = getSessionInfo(eventProto);
                ConnectionRequestInfo connectionRequestInfo = getConnectionRequestInfo(msg);
                return new ConnectionRequestMsg(callback, sessionInfo, connectionRequestInfo);
            }
            case DISCONNECTION_REQUEST -> {
                var sessionInfo = getSessionInfo(eventProto);
                return new SessionDisconnectedMsg(callback, sessionInfo.getSessionId(), sessionInfo.getSessionExpiryInterval());
            }
            case CLEAR_SESSION_REQUEST -> {
                var sessionInfo = getSessionInfo(eventProto);
                return new ClearSessionMsg(callback, sessionInfo.getSessionId());
            }
            case REMOVE_APPLICATION_TOPIC_REQUEST -> {
                return new RemoveApplicationTopicRequestMsg(callback);
            }
            default ->
                    throw new RuntimeException("Unexpected ClientSessionEventType - " + ClientSessionEventType.class.getSimpleName());
        }
    }

    private SessionInfo getSessionInfo(ClientSessionEventProto eventProto) {
        return ProtoConverter.convertToSessionInfo(eventProto.getSessionInfo());
    }

    private ConnectionRequestInfo getConnectionRequestInfo(TbProtoQueueMsg<ClientSessionEventProto> msg) {
        TbQueueMsgHeaders requestHeaders = msg.getHeaders();
        long requestTime = bytesToLong(requestHeaders.get(REQUEST_TIME));
        UUID requestId = bytesToUuid(requestHeaders.get(REQUEST_ID_HEADER));
        String responseTopic = bytesToString(requestHeaders.get(RESPONSE_TOPIC_HEADER));
        return new ConnectionRequestInfo(requestId, requestTime, responseTopic);
    }
}
