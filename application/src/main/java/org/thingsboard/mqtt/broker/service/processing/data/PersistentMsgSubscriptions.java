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
package org.thingsboard.mqtt.broker.service.processing.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.gen.queue.PublishMsgProto;
import org.thingsboard.mqtt.broker.service.processing.MsgProcessingCallback;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
public class PersistentMsgSubscriptions {

    private final boolean processSubscriptionsInParallel;

    private List<Subscription> deviceSubscriptions;
    private List<Subscription> applicationSubscriptions;
    private Set<Subscription> allApplicationSharedSubscriptions;
    private List<Subscription> integrationSubscriptions;

    public PersistentMsgSubscriptions() {
        this.processSubscriptionsInParallel = false;
    }

    public PersistentMsgSubscriptions(boolean processSubscriptionsInParallel, Set<Subscription> allApplicationSharedSubscriptions) {
        this.processSubscriptionsInParallel = processSubscriptionsInParallel;
        this.allApplicationSharedSubscriptions = allApplicationSharedSubscriptions;
    }

    public static PersistentMsgSubscriptions newInstance(MsgSubscriptions msgSubscriptions, boolean processSubscriptionsInParallel) {
        return new PersistentMsgSubscriptions(processSubscriptionsInParallel, msgSubscriptions.getAllApplicationSharedSubscriptions());
    }

    public boolean isNotEmpty() {
        return !CollectionUtils.isEmpty(deviceSubscriptions) ||
                !CollectionUtils.isEmpty(applicationSubscriptions) ||
                !CollectionUtils.isEmpty(allApplicationSharedSubscriptions) ||
                !CollectionUtils.isEmpty(integrationSubscriptions);
    }

    public void addToDevices(Subscription subscription, int size) {
        if (deviceSubscriptions == null) {
            deviceSubscriptions = initArrayList(size);
        }
        deviceSubscriptions.add(subscription);
    }

    public void addToApplications(Subscription subscription, int size) {
        if (applicationSubscriptions == null) {
            applicationSubscriptions = initArrayList(size);
        }
        applicationSubscriptions.add(subscription);
    }

    public void addToIntegrations(Subscription subscription, int size) {
        if (integrationSubscriptions == null) {
            integrationSubscriptions = initArrayList(size);
        }
        integrationSubscriptions.add(subscription);
    }

    private List<Subscription> initArrayList(int size) {
        return processSubscriptionsInParallel ? Collections.synchronizedList(new ArrayList<>(size)) : new ArrayList<>(size);
    }


    public void processSubscriptions(List<Subscription> subscriptions, PublishMsgProto publishMsgProto,
                                     MsgProcessingCallback callback) {
        if (isNonPersistentByPubQos(publishMsgProto)) {
            if (processSubscriptionsInParallel) {
                subscriptions.parallelStream().forEach(subscription -> {
                    addToIntegrations(subscription, subscriptions.size());
                    callback.accept(subscription);
                });
            } else {
                for (Subscription subscription : subscriptions) {
                    addToIntegrations(subscription, subscriptions.size());
                    callback.accept(subscription);
                }
            }
        } else {
            process(subscriptions, callback);
        }
    }

    public void process(List<Subscription> subscriptions, MsgProcessingCallback callback) {
        int size = subscriptions.size();
        if (processSubscriptionsInParallel) {
            subscriptions.parallelStream().forEach(subscription -> processSubscription(subscription, size, callback));
        } else {
            for (Subscription subscription : subscriptions) {
                processSubscription(subscription, size, callback);
            }
        }
    }

    private void processSubscription(Subscription subscription, int size, MsgProcessingCallback callback) {
        if (isPersistentBySubInfo(subscription)) {
            switch (subscription.getClientType()) {
                case APPLICATION -> addToApplications(subscription, size);
                case DEVICE -> addToDevices(subscription, size);
                case INTEGRATION -> addToIntegrations(subscription, size);
            }
        } else {
            callback.accept(subscription);
        }
    }

    private boolean isPersistentBySubInfo(Subscription subscription) {
        return subscription.getClientSessionInfo().isPersistent() && isQosPersistent(subscription);
    }

    private boolean isQosPersistent(Subscription subscription) {
        return subscription.getQos() != MqttQoS.AT_MOST_ONCE.value();
    }

    private boolean isNonPersistentByPubQos(PublishMsgProto publishMsgProto) {
        return publishMsgProto.getQos() == MqttQoS.AT_MOST_ONCE.value();
    }
}
