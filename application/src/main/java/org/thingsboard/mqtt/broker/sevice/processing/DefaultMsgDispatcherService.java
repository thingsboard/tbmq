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
package org.thingsboard.mqtt.broker.sevice.processing;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.queue.TopicInfo;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.SessionInfoProto;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.TbQueueConsumer;
import org.thingsboard.mqtt.broker.queue.TbQueueProducer;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.sevice.subscription.Subscription;
import org.thingsboard.mqtt.broker.sevice.subscription.SubscriptionService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DefaultMsgDispatcherService implements MsgDispatcherService {

    private final SubscriptionService subscriptionService;
    private final TbQueueProducer<TbProtoQueueMsg<PublishMsgProto>> publishMsgProducer;

    public DefaultMsgDispatcherService(SubscriptionService subscriptionService,
                                       PublishMsgQueueFactory publishMsgQueueFactory) {
        this.subscriptionService = subscriptionService;
        this.publishMsgProducer = publishMsgQueueFactory.createProducer();
    }

    @Override
    public void acknowledgePublishMsg(SessionInfoProto sessionInfoProto, MqttPublishMessage publishMessage, TbQueueCallback callback) {
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishProtoMessage(sessionInfoProto, publishMessage);
        String topicName = publishMessage.variableHeader().topicName();
        TopicInfo topicInfo = new TopicInfo(publishMsgProducer.getDefaultTopic());
        publishMsgProducer.send(topicInfo, new TbProtoQueueMsg<>(topicName, publishMsgProto), callback);
    }

    @Override
    public void processPublishMsg(PublishMsgProto publishMsgProto) {
        // todo: persist if required

        for (Subscription subscription : subscriptionService.getSubscriptions(publishMsgProto.getTopicName())) {
            subscription.getListener().onPublishMsg(subscription.getMqttQoS(), publishMsgProto);
        }
    }


}
