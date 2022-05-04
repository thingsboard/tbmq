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
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscriptionCache;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionSubscriptionServiceImpl implements SessionSubscriptionService {
    private final ClientSessionCache clientSessionCache;
    private final ClientSubscriptionCache subscriptionCache;

    @Override
    public DetailedClientSessionInfoDto getDetailedClientSessionInfo(String clientId) throws ThingsboardException {
        ClientSessionInfo clientSessionInfo = clientSessionCache.getClientSessionInfo(clientId);
        if (clientSessionInfo == null) {
            throw new ThingsboardException(ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        ClientSession clientSession = clientSessionInfo.getClientSession();
        SessionInfo sessionInfo = clientSession.getSessionInfo();
        ConnectionInfo connectionInfo = sessionInfo.getConnectionInfo();
        Set<TopicSubscription> subscriptions = subscriptionCache.getClientSubscriptions(clientId);

        return DetailedClientSessionInfoDto.builder()
                .id(sessionInfo.getClientInfo().getClientId())
                .clientId(sessionInfo.getClientInfo().getClientId())
                .sessionId(sessionInfo.getSessionId())
                .clientType(sessionInfo.getClientInfo().getType())
                .connectionState(clientSession.isConnected() ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED)
                .nodeId(sessionInfo.getServiceId())
                .persistent(sessionInfo.isPersistent())
                .subscriptions(collectSubscriptions(subscriptions))
                .keepAliveSeconds(connectionInfo.getKeepAlive())
                .connectedAt(connectionInfo.getConnectedAt())
                .disconnectedAt(connectionInfo.getDisconnectedAt())
                .build();
    }

    private List<SubscriptionInfoDto> collectSubscriptions(Set<TopicSubscription> subscriptions) {
        return subscriptions.stream()
                .map(topicSubscription -> new SubscriptionInfoDto(
                        topicSubscription.getTopic(),
                        MqttQoS.valueOf(topicSubscription.getQos())))
                .collect(Collectors.toList());
    }
}
