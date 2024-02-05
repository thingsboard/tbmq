/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.subscription;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class Subscription {

    private final String topicFilter;
    private final int qos;
    private final ClientSessionInfo clientSessionInfo;
    private final String shareName;
    private final SubscriptionOptions options;

    public Subscription(String topicFilter, int qos, ClientSessionInfo clientSessionInfo) {
        this(topicFilter, qos, clientSessionInfo, null, SubscriptionOptions.newInstance());
    }

    public Subscription(String topicFilter, ClientSessionInfo clientSessionInfo, String shareName) {
        this(topicFilter, 0, clientSessionInfo, shareName, SubscriptionOptions.newInstance());
    }

    public static Subscription newInstance(String topicFilter, int qos, ClientSession clientSession) {
        return new Subscription(topicFilter, qos, ClientSessionInfoFactory.clientSessionToClientSessionInfo(clientSession));
    }

    public String getClientId() {
        return clientSessionInfo.getClientId();
    }

    public String getServiceId() {
        return clientSessionInfo.getServiceId();
    }

    public ClientType getClientType() {
        return clientSessionInfo.getType();
    }

    public boolean isConnected() {
        return clientSessionInfo.isConnected();
    }

}
