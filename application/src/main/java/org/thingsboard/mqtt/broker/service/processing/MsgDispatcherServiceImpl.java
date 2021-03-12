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
package org.thingsboard.mqtt.broker.service.processing;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsgDeliveryService;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.client.ClientSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionManager;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.SessionState;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MsgDispatcherServiceImpl implements MsgDispatcherService {

    private final SubscriptionManager subscriptionManager;
    private final PublishMsgQueueFactory publishMsgQueueFactory;
    private final StatsManager statsManager;
    private final MsgPersistenceManager msgPersistenceManager;
    private final ClientSessionService clientSessionService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final PublishMsgDeliveryService publishMsgDeliveryService;


    private TbQueueProducer<TbProtoQueueMsg<PublishMsgProto>> publishMsgProducer;
    private MessagesStats producerStats;

    @PostConstruct
    public void init() {
        this.publishMsgProducer = publishMsgQueueFactory.createProducer();
        this.producerStats = statsManager.createMsgDispatcherPublishStats();
    }

    @PreDestroy
    public void destroy() {
        publishMsgProducer.stop();
    }

    @Override
    public void acknowledgePublishMsg(SessionInfo sessionInfo, PublishMsg publishMsg, TbQueueCallback callback) {
        log.trace("[{}] Acknowledging publish msg [topic:[{}], qos:[{}]].", sessionInfo.getClientInfo().getClientId(), publishMsg.getTopicName(), publishMsg.getQosLevel());
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishProtoMessage(sessionInfo, publishMsg);
        producerStats.incrementTotal();
        callback = statsManager.wrapTbQueueCallback(callback, producerStats);
        publishMsgProducer.send(new TbProtoQueueMsg<>(publishMsg.getTopicName(), publishMsgProto), callback);
    }

    @Override
    public void processPublishMsg(PublishMsgProto publishMsgProto, PublishMsgCallback callback) {
        // TODO: log time for getting subscriptions
        Collection<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters = subscriptionManager.getSubscriptions(publishMsgProto.getTopicName());
        Collection<ValueWithTopicFilter<ClientSubscription>> filteredClientSubscriptions = filterHighestQosClientSubscriptions(clientSubscriptionWithTopicFilters);
        // TODO: log time for getting clients
        List<Subscription> msgSubscriptions = filteredClientSubscriptions.stream()
                .map(clientSubscription -> {
                    String clientId = clientSubscription.getValue().getClientId();
                    ClientSession clientSession = clientSessionService.getClientSession(clientId);
                    ClientSessionCtx clientSessionCtx = clientSessionCtxService.getClientSessionCtx(clientId);
                    return new Subscription(clientSubscription.getTopicFilter(), clientSubscription.getValue().getQosValue(),
                            clientSession.getSessionInfo(), clientSessionCtx);
                })
                .collect(Collectors.toList());

        List<Subscription> persistentSubscriptions = new ArrayList<>();
        for (Subscription msgSubscription : msgSubscriptions) {
            if (needToBePersisted(publishMsgProto, msgSubscription)) {
                persistentSubscriptions.add(msgSubscription);
            } else {
                trySendMsg(publishMsgProto, msgSubscription);
            }
        }
        if (!persistentSubscriptions.isEmpty()) {
            // TODO: process messages one by one (retrying to save message could lead to wrong order)
            msgPersistenceManager.processPublish(publishMsgProto, persistentSubscriptions, callback);
        } else {
            callback.onSuccess();
        }
    }

    private Collection<ValueWithTopicFilter<ClientSubscription>> filterHighestQosClientSubscriptions(Collection<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters) {
        return clientSubscriptionWithTopicFilters.stream()
                .collect(Collectors.toMap(clientSubscriptionWithTopicFilter -> clientSubscriptionWithTopicFilter.getValue().getClientId(),
                        Function.identity(),
                        (first, second) -> first.getValue().getQosValue() > second.getValue().getQosValue() ? first : second))
                .values();
    }

    private boolean needToBePersisted(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription) {
        return subscription.getSessionInfo().isPersistent()
                && subscription.getMqttQoSValue() != MqttQoS.AT_MOST_ONCE.value()
                && publishMsgProto.getQos() != MqttQoS.AT_MOST_ONCE.value();
    }

    private void trySendMsg(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription) {
        ClientSessionCtx sessionCtx = subscription.getSessionCtx();
        if (sessionCtx == null || sessionCtx.getSessionState() != SessionState.CONNECTED) {
            return;
        }
        int packetId = subscription.getMqttQoSValue() == MqttQoS.AT_MOST_ONCE.value() ? -1 : sessionCtx.nextMsgId();
        int minQoSValue = Math.min(subscription.getMqttQoSValue(), publishMsgProto.getQos());
        MqttQoS mqttQoS = MqttQoS.valueOf(minQoSValue);
        publishMsgDeliveryService.sendPublishMsgToClient(sessionCtx, packetId, publishMsgProto.getTopicName(),
                mqttQoS, publishMsgProto.getPayload().toByteArray());
    }
}
