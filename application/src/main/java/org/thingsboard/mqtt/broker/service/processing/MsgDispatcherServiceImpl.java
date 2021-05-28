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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkPublisher;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionReader;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MsgDispatcherServiceImpl implements MsgDispatcherService {

    @Autowired
    private SubscriptionReader subscriptionReader;
    @Autowired
    private PublishMsgQueueFactory publishMsgQueueFactory;
    @Autowired
    private StatsManager statsManager;
    @Autowired
    private MsgPersistenceManager msgPersistenceManager;
    @Autowired
    private ClientSessionService clientSessionService;
    @Autowired
    private DownLinkPublisher downLinkPublisher;


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
    public void persistPublishMsg(SessionInfo sessionInfo, PublishMsg publishMsg, TbQueueCallback callback) {
        log.trace("[{}] Persisting publish msg [topic:[{}], qos:[{}]].", sessionInfo.getClientInfo().getClientId(), publishMsg.getTopicName(), publishMsg.getQosLevel());
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishProtoMessage(sessionInfo, publishMsg);
        producerStats.incrementTotal();
        callback = statsManager.wrapTbQueueCallback(callback, producerStats);
        publishMsgProducer.send(new TbProtoQueueMsg<>(publishMsg.getTopicName(), publishMsgProto), callback);
    }

    @Override
    public void processPublishMsg(PublishMsgProto publishMsgProto, PublishMsgCallback callback) {
        // TODO: log time for getting subscriptions
        Collection<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters = subscriptionReader.getSubscriptions(publishMsgProto.getTopicName());
        Collection<ValueWithTopicFilter<ClientSubscription>> filteredClientSubscriptions = filterHighestQosClientSubscriptions(clientSubscriptionWithTopicFilters);
        // TODO: log time for getting clients
        List<Subscription> msgSubscriptions = filteredClientSubscriptions.stream()
                .map(clientSubscription -> {
                    String clientId = clientSubscription.getValue().getClientId();
                    ClientSession clientSession = clientSessionService.getClientSession(clientId);
                    if (clientSession == null) {
                        log.info("[{}] Client session not found for existent client subscription.", clientId);
                        return null;
                    }
                    return new Subscription(clientSubscription.getTopicFilter(), clientSubscription.getValue().getQosValue(),
                            clientSession.getSessionInfo());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Subscription> persistentSubscriptions = new ArrayList<>();
        for (Subscription msgSubscription : msgSubscriptions) {
            if (needToBePersisted(publishMsgProto, msgSubscription)) {
                persistentSubscriptions.add(msgSubscription);
            } else {
                sendToNode(createBasicPublishMsg(msgSubscription, publishMsgProto), msgSubscription);
            }
        }
        if (!persistentSubscriptions.isEmpty()) {
            // TODO: convert Proto msg to PublishMsg
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

    private void sendToNode(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription) {
        String targetServiceId = subscription.getSessionInfo().getServiceId();
        String clientId = subscription.getSessionInfo().getClientInfo().getClientId();
        downLinkPublisher.publishBasicMsg(targetServiceId, clientId, publishMsgProto);
    }

    private QueueProtos.PublishMsgProto createBasicPublishMsg(Subscription clientSubscription, QueueProtos.PublishMsgProto publishMsgProto) {
        int minQoSValue = Math.min(clientSubscription.getMqttQoSValue(), publishMsgProto.getQos());
        return publishMsgProto.toBuilder()
                .setQos(minQoSValue)
                .build();
    }
}
