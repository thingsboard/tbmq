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
package org.thingsboard.mqtt.broker.service.processing.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientSessionInfo;
import org.thingsboard.mqtt.broker.common.data.subscription.SubscriptionOptions;
import org.thingsboard.mqtt.broker.queue.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategy;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessingStrategyFactory;
import org.thingsboard.mqtt.broker.service.subscription.shared.TopicSharedSubscription;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceSharedSubscriptionProcessorImpl implements DeviceSharedSubscriptionProcessor {

    private final ServiceInfoProvider serviceInfoProvider;
    private final SharedSubscriptionProcessingStrategyFactory sharedSubscriptionProcessingStrategyFactory;
    private final ClientSessionCtxService clientSessionCtxService;

    @Override
    public List<Subscription> getTargetSubscriptions(Set<Subscription> deviceSubscriptions, int qos) {
        if (CollectionUtils.isEmpty(deviceSubscriptions)) {
            return null;
        }
        List<SharedSubscription> sharedSubscriptionList = toSharedSubscriptionList(deviceSubscriptions);
        return collectOneSubscriptionFromEveryDeviceSharedSubscription(sharedSubscriptionList, qos);
    }

    List<SharedSubscription> toSharedSubscriptionList(Set<Subscription> sharedSubscriptions) {
        return sharedSubscriptions.stream()
                .collect(Collectors.groupingBy(subscription ->
                        new TopicSharedSubscription(subscription.getTopicFilter(), subscription.getShareName())))
                .entrySet().stream()
                .map(entry -> new SharedSubscription(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private List<Subscription> collectOneSubscriptionFromEveryDeviceSharedSubscription(List<SharedSubscription> sharedSubscriptions, int qos) {
        List<Subscription> result = new ArrayList<>(sharedSubscriptions.size());
        for (SharedSubscription sharedSubscription : sharedSubscriptions) {
            result.add(getSubscription(sharedSubscription, qos));
        }
        return result;
    }

    private Subscription getSubscription(SharedSubscription sharedSubscription, int qos) {
        Subscription anyActive = findAnyConnectedSubscription(sharedSubscription.getSubscriptions());
        if (anyActive == null) {
            log.info("[{}] No active subscription found for shared subscription - all are persisted and disconnected", sharedSubscription.getTopicSharedSubscription());
            return createDummySubscription(sharedSubscription, qos);
        } else {
            SharedSubscriptionProcessingStrategy strategy = sharedSubscriptionProcessingStrategyFactory.newInstance();
            return strategy.analyze(sharedSubscription);
        }
    }

    Subscription findAnyConnectedSubscription(List<Subscription> subscriptions) {
        if (CollectionUtils.isEmpty(subscriptions)) {
            return null;
        }
        return subscriptions
                .stream()
                .filter(subscription -> subscription.getClientSessionInfo().isConnected())
                .filter(subscription -> clientSessionCtxService.getClientSessionCtx(subscription.getClientId()).isWritable())
                .findAny()
                .orElse(null);
    }

    private Subscription createDummySubscription(SharedSubscription sharedSubscription, int qos) {
        return new Subscription(
                sharedSubscription.getTopicSharedSubscription().getTopicFilter(),
                qos,
                createDummyClientSession(sharedSubscription),
                sharedSubscription.getTopicSharedSubscription().getShareName(),
                SubscriptionOptions.newInstance(),
                -1
        );
    }

    private ClientSessionInfo createDummyClientSession(SharedSubscription sharedSubscription) {
        ClientInfo clientInfo = ClientSessionInfoFactory.getClientInfo(sharedSubscription.getTopicSharedSubscription().getKey());
        return ClientSessionInfo.builder()
                .connected(false)
                .serviceId(serviceInfoProvider.getServiceId())
                .clientId(clientInfo.getClientId())
                .type(clientInfo.getType())
                .clientIpAdr(clientInfo.getClientIpAdr())
                .cleanStart(false)
                .sessionExpiryInterval(1000)
                .build();
    }

}
