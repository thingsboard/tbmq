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
package org.thingsboard.mqtt.broker.service.mqtt.client.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.ConnectionInfo;
import org.thingsboard.mqtt.broker.common.data.ConnectionState;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.dto.DetailedClientSessionInfoDto;
import org.thingsboard.mqtt.broker.dto.SubscriptionInfoDto;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionReader;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionSubscriptionServiceImpl implements SessionSubscriptionService {
    private final ClientSessionReader clientSessionReader;
    private final ClientSubscriptionReader subscriptionReader;

    @Override
    public DetailedClientSessionInfoDto getDetailedClientSessionInfo(String clientId) throws ThingsboardException {
        ClientSessionInfo clientSessionInfo = clientSessionReader.getClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            throw new ThingsboardException(ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        ClientSession clientSession = clientSessionInfo.getClientSession();
        SessionInfo sessionInfo = clientSession.getSessionInfo();
        ConnectionInfo connectionInfo = sessionInfo.getConnectionInfo();
        Set<TopicSubscription> subscriptions = subscriptionReader.getClientSubscriptions(clientId);

        return DetailedClientSessionInfoDto.builder()
                .clientId(sessionInfo.getClientInfo().getClientId())
                .sessionId(sessionInfo.getSessionId())
                .clientType(sessionInfo.getClientInfo().getType())
                .connectionState(clientSession.isConnected() ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED)
                .nodeId(sessionInfo.getServiceId())
                .persistent(sessionInfo.isPersistent())
                .subscriptions(subscriptions.stream()
                        .map(topicSubscription -> new SubscriptionInfoDto(topicSubscription.getTopic(), MqttQoS.valueOf(topicSubscription.getQos())))
                        .collect(Collectors.toList()))
                .keepAliveSeconds(connectionInfo.getKeepAlive())
                .connectedAt(connectionInfo.getConnectedAt())
                .disconnectedAt(connectionInfo.getDisconnectedAt())
                .build()
                ;
    }
}
