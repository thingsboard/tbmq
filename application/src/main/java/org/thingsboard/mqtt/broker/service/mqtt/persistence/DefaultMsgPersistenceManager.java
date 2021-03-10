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
package org.thingsboard.mqtt.broker.service.mqtt.persistence;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.ApplicationPersistenceSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.application.processing.ApplicationPersistenceProcessor;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.device.DevicePersistenceSessionService;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionListener;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.thingsboard.mqtt.broker.common.data.ClientType.APPLICATION;
import static org.thingsboard.mqtt.broker.common.data.ClientType.DEVICE;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultMsgPersistenceManager implements MsgPersistenceManager, SubscriptionListener {

    private final ApplicationPersistenceSessionService applicationPersistenceSessionService;
    private final ApplicationPersistenceProcessor applicationPersistenceProcessor;

    private final DevicePersistenceSessionService devicePersistenceSessionService;

    // TODO: think about case when client is DEVICE and then is changed to APPLICATION and vice versa

    @Override
    public void processPublish(QueueProtos.PublishMsgProto publishMsgProto, Collection<Subscription> persistentSubscriptions) {
        List<Subscription> deviceSubscriptions = new ArrayList<>();
        for (Subscription msgSubscription : persistentSubscriptions) {
            ClientInfo clientInfo = msgSubscription.getSessionInfo().getClientInfo();
            String clientId = clientInfo.getClientId();
            ClientType clientType = clientInfo.getType();
            if (clientType == DEVICE) {
                deviceSubscriptions.add(msgSubscription);
            } else if (clientType == APPLICATION) {
                applicationPersistenceSessionService.processMsgPersistence(clientId, msgSubscription.getMqttQoSValue(), publishMsgProto);
            } else {
                log.warn("[{}] Persistence for clientType {} is not supported.", clientId, clientType);
            }
        }
        devicePersistenceSessionService.processMsgPersistence(deviceSubscriptions, publishMsgProto);
    }

    @Override
    public void processPersistedMessages(ClientSessionCtx clientSessionCtx) {
        ClientType clientType = clientSessionCtx.getSessionInfo().getClientInfo().getType();
        if (clientType == APPLICATION) {
            applicationPersistenceProcessor.startProcessingPersistedMessages(clientSessionCtx);
        } else if (clientType == DEVICE) {
            devicePersistenceSessionService.startProcessingPersistedMessages(clientSessionCtx);
        }
    }

    @Override
    public void stopProcessingPersistedMessages(ClientInfo clientInfo) {
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.stopProcessingPersistedMessages(clientInfo.getClientId());
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceSessionService.stopProcessingPersistedMessages(clientInfo.getClientId());
            // TODO: stop select query if it's running
        } else {
            log.warn("[{}] Persisted messages are not supported for client type {}.", clientInfo.getClientId(), clientInfo.getType());
        }
    }

    @Override
    public void clearPersistedMessages(ClientInfo clientInfo) {
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.clearPersistedMsgs(clientInfo.getClientId());
            applicationPersistenceSessionService.clearPersistedCtx(clientInfo.getClientId());
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceSessionService.clearPersistedCtx(clientInfo.getClientId());
        }
    }

    @Override
    public void acknowledgePersistedMsgDelivery(int packetId, ClientSessionCtx clientSessionCtx) {
        ClientInfo clientInfo = clientSessionCtx.getSessionInfo().getClientInfo();
        String clientId = clientInfo.getClientId();
        if (clientInfo.getType() == APPLICATION) {
            applicationPersistenceProcessor.acknowledgeDelivery(clientId, packetId);
        } else if (clientInfo.getType() == DEVICE) {
            devicePersistenceSessionService.acknowledgeDelivery(clientId, packetId);
        }
    }

    @Override
    public void onSubscribe(SessionInfo sessionInfo, List<TopicSubscription> topicSubscriptions) {
        if (sessionInfo.isPersistent() && sessionInfo.getClientInfo().getType() == DEVICE) {
            devicePersistenceSessionService.processSubscribe(sessionInfo.getClientInfo().getClientId(), topicSubscriptions);
        }
    }

    @Override
    public void onUnsubscribe(SessionInfo sessionInfo, List<String> topicFilters) {
        if (sessionInfo.isPersistent() && sessionInfo.getClientInfo().getType() == DEVICE) {
            devicePersistenceSessionService.processUnsubscribe(sessionInfo.getClientInfo().getClientId(), topicFilters);
        }
    }
}
