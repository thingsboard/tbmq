/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.standalone.event;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.broker.actors.client.messages.ClientCallback;
import org.thingsboard.mqtt.broker.actors.client.messages.cluster.SessionClusterManagementMsg;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.util.BytesUtil;
import org.thingsboard.mqtt.broker.common.util.edition.StandaloneComponent;
import org.thingsboard.mqtt.broker.gen.queue.ClientSessionEventProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionCallbackMsgFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventActorManager;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventFactory;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientCleanupInfo;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.data.ClientConnectInfo;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;

import java.util.UUID;

import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.REQUEST_ID_HEADER;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.REQUEST_TIME;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.RESPONSE_TOPIC_HEADER;

/**
 * Hands the session events to the client actor directly, the way the queue consumer does in the cluster edition.
 */
@Slf4j
@Component
@StandaloneComponent
@RequiredArgsConstructor
public class StandaloneClientSessionEventService implements ClientSessionEventService {

    static final String LOCAL_RESPONSE_TOPIC = "local";

    private final ClientSessionEventFactory eventFactory;
    private final ClientSessionCallbackMsgFactory callbackMsgFactory;
    private final ClientSessionEventActorManager actorManager;
    private final PendingConnectionRequests pendingConnectionRequests;

    @Override
    public ListenableFuture<ConnectionResponse> requestConnection(SessionInfo sessionInfo, ClientConnectInfo clientConnectInfo) {
        UUID requestId = UUID.randomUUID();
        ListenableFuture<ConnectionResponse> future = pendingConnectionRequests.register(requestId);
        if (future.isDone()) {
            return future;
        }
        send(sessionInfo.getClientId(),
                eventFactory.createConnectionRequestEventProto(sessionInfo, clientConnectInfo),
                requestId,
                new ClientCallback() {
                    @Override
                    public void onSuccess() {
                        // the answer reaches the future through LocalClientSessionEventResponseSender
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        pendingConnectionRequests.fail(requestId, t);
                    }
                });
        return future;
    }

    @Override
    public void notifyClientDisconnected(SessionInfo sessionInfo, DisconnectReasonType reasonType, TbQueueCallback callback) {
        send(sessionInfo.getClientId(),
                eventFactory.createDisconnectedEventProto(sessionInfo, reasonType),
                UUID.randomUUID(),
                toClientCallback(callback));
    }

    @Override
    public void requestClientSessionCleanup(ClientSessionInfo clientSessionInfo, ClientCleanupInfo clientCleanupInfo) {
        send(clientSessionInfo.getClientId(),
                eventFactory.createClearSessionRequestEventProto(clientSessionInfo, clientCleanupInfo),
                UUID.randomUUID(),
                toClientCallback(null));
    }

    @Override
    public void requestApplicationTopicRemoved(String clientId) {
        log.debug("[{}] Application topic removal is not needed without a queue.", clientId);
    }

    private void send(String clientId, ClientSessionEventProto eventProto, UUID requestId, ClientCallback callback) {
        TbProtoQueueMsg<ClientSessionEventProto> eventRequest = new TbProtoQueueMsg<>(clientId, eventProto);
        eventRequest.getHeaders().put(REQUEST_ID_HEADER, BytesUtil.uuidToBytes(requestId));
        eventRequest.getHeaders().put(REQUEST_TIME, BytesUtil.longToBytes(System.currentTimeMillis()));
        eventRequest.getHeaders().put(RESPONSE_TOPIC_HEADER, BytesUtil.stringToBytes(LOCAL_RESPONSE_TOPIC));

        log.trace("[{}][{}][{}] Sending client session event request.", clientId, eventProto.getEventType(), requestId);
        try {
            SessionClusterManagementMsg msg = callbackMsgFactory.createSessionClusterManagementMsg(eventRequest, callback);
            actorManager.sendSessionClusterManagementMsg(clientId, msg);
        } catch (Exception e) {
            log.debug("[{}][{}][{}] Failed to send request.", clientId, eventProto.getEventType(), requestId, e);
            callback.onFailure(e);
        }
    }

    private ClientCallback toClientCallback(TbQueueCallback callback) {
        return new ClientCallback() {
            @Override
            public void onSuccess() {
                if (callback != null) {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (callback != null) {
                    callback.onFailure(t);
                }
            }
        };
    }

}
