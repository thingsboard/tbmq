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

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.With;
import org.thingsboard.mqtt.broker.common.data.ClientSession;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Data
public class Subscription {

    private final String topicFilter;
    private final int qos;
    @With
    private final ClientSessionInfo clientSessionInfo;
    private final String shareName;
    private final SubscriptionOptions options;
    private final List<Integer> subscriptionIds;

    public Subscription(String topicFilter, int qos, ClientSessionInfo clientSessionInfo, String shareName,
                        SubscriptionOptions options, int subscriptionId) {
        this(topicFilter, qos, clientSessionInfo, shareName, options,
                subscriptionId == -1 ? Lists.newArrayList() : Lists.newArrayList(subscriptionId));
    }

    /**
     * These constructors and newInstance method are used only for tests
     */
    public Subscription(String topicFilter, int qos, ClientSessionInfo clientSessionInfo) {
        this(topicFilter, qos, clientSessionInfo, null, SubscriptionOptions.newInstance(), Collections.emptyList());
    }

    public Subscription(String topicFilter, ClientSessionInfo clientSessionInfo, String shareName) {
        this(topicFilter, 0, clientSessionInfo, shareName, SubscriptionOptions.newInstance(), Collections.emptyList());
    }

    public Subscription(String topicFilter, int qos, ClientSessionInfo clientSessionInfo, String shareName, SubscriptionOptions options) {
        this(topicFilter, qos, clientSessionInfo, shareName, options, Collections.emptyList());
    }

    public Subscription(String topicFilter, int qos, List<Integer> subscriptionIds) {
        this(topicFilter, qos, ClientSessionInfo.builder().build(), null, SubscriptionOptions.newInstance(), subscriptionIds);
    }

    public static Subscription newInstance(String topicFilter, int qos, ClientSession clientSession) {
        return new Subscription(topicFilter, qos, ClientSessionInfoFactory.clientSessionToClientSessionInfo(clientSession));
    }

    /**
     * Helper methods
     */
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

    public Subscription compareAndGetHigherQosAndAllSubscriptionIds(Subscription another) {
        if (this.getQos() > another.getQos()) {
            this.addAllSubscriptionIds(another);
            return this;
        }
        another.addAllSubscriptionIds(this);
        return another;
    }

    public void addAllSubscriptionIds(Subscription source) {
        subscriptionIds.addAll(source.getSubscriptionIds());
    }

    public boolean isSubsIdsPresent() {
        return !subscriptionIds.isEmpty();
    }
}
