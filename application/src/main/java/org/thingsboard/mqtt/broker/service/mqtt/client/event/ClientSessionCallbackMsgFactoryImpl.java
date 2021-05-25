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
package org.thingsboard.mqtt.broker.service.mqtt.client.event;

import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.session.messages.CallbackMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.ClearSessionMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.ClientSessionCallback;
import org.thingsboard.mqtt.broker.actors.session.messages.ConnectionRequestInfo;
import org.thingsboard.mqtt.broker.actors.session.messages.ConnectionRequestMsg;
import org.thingsboard.mqtt.broker.actors.session.messages.SessionDisconnectedMsg;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueMsgHeaders;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;

import java.util.UUID;

import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.REQUEST_ID_HEADER;
import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.REQUEST_TIME;
import static org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventConst.RESPONSE_TOPIC_HEADER;
import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToLong;
import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToString;
import static org.thingsboard.mqtt.broker.util.BytesUtil.bytesToUuid;

@Service
public class ClientSessionCallbackMsgFactoryImpl implements ClientSessionCallbackMsgFactory {
    @Override
    public CallbackMsg createCallbackMsg(TbProtoQueueMsg<QueueProtos.ClientSessionEventProto> msg, ClientSessionCallback callback) {
        QueueProtos.ClientSessionEventProto eventProto = msg.getValue();
        SessionInfo sessionInfo = ProtoConverter.convertToSessionInfo(eventProto.getSessionInfo());
        switch (ClientSessionEventType.valueOf(eventProto.getEventType())) {
            case CONNECTION_REQUEST:
                TbQueueMsgHeaders requestHeaders = msg.getHeaders();
                long requestTime = bytesToLong(requestHeaders.get(REQUEST_TIME));
                UUID requestId = bytesToUuid(requestHeaders.get(REQUEST_ID_HEADER));
                String responseTopic = bytesToString(requestHeaders.get(RESPONSE_TOPIC_HEADER));
                ConnectionRequestInfo connectionRequestInfo = new ConnectionRequestInfo(requestId, requestTime, responseTopic);
                return new ConnectionRequestMsg(callback, sessionInfo, connectionRequestInfo);
            case DISCONNECTED:
                return new SessionDisconnectedMsg(callback, sessionInfo.getSessionId());
            case TRY_CLEAR_SESSION_REQUEST:
                return new ClearSessionMsg(callback, sessionInfo.getSessionId());
            default:
                throw new RuntimeException("Unexpected ClientSessionEvent type.");
        }
    }
}
